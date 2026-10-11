package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.CustomRule
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.RuleClassPreview
import com.github.kr328.clash.core.model.RulePreviewLine
import com.github.kr328.clash.core.model.RuleSetConfig
import com.github.kr328.clash.core.model.parseRuleLine
import com.github.kr328.clash.service.data.RuleSet
import com.github.kr328.clash.service.data.RuleSetDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.yaml.snakeyaml.Yaml
import java.util.UUID

object RuleConfigManager {
    private val applyLock = Mutex()

    private val builtinPolicies = setOf(
        "DIRECT",
        "REJECT",
        "REJECT-DROP",
        "PASS",
        "PASS-RULE",
        "COMPATIBLE",
        "GLOBAL",
    )

    private val json = Json { ignoreUnknownKeys = true }

    data class BaseConfig(
        val rules: List<String>,
        val proxyGroups: List<Map<String, Any?>>,
        val defaultGroup: String?,
    )

    data class MergedConfig(
        val rules: List<String>,
        val proxyGroups: List<JsonObject>,
    )

    fun parseBaseConfig(context: Context, profileUuid: UUID): BaseConfig? {
        val file = context.importedDir.resolve(profileUuid.toString()).resolve("config.yaml")
        if (!file.isFile)
            return null

        return try {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load<Any?>(file.readText()) as? Map<String, Any?> ?: return null

            val rules = (root["rules"] as? List<Any?>)?.mapNotNull { it as? String } ?: emptyList()
            val proxyGroups = (root["proxy-groups"] as? List<Any?>)?.mapNotNull { entry ->
                (entry as? Map<Any?, Any?>)?.mapKeys { (key, _) -> key.toString() }
            } ?: emptyList()
            val defaultGroup = proxyGroups.firstOrNull {
                when ((it["type"] as? String)?.lowercase()) {
                    "select", "selector" -> true
                    else -> false
                }
            }?.get("name") as? String

            BaseConfig(rules, proxyGroups, defaultGroup)
        } catch (e: Exception) {
            Log.w("Parse base config for $profileUuid failed: ${e.message}", e)

            null
        }
    }

    fun buildMerged(base: BaseConfig, activeSet: RuleSetConfig?): MergedConfig? {
        val customGroups = activeSet?.groups.orEmpty()
        if (activeSet == null || customGroups.isEmpty())
            return null

        val baseGroupNames = base.proxyGroups.mapNotNull { it["name"] as? String }.toSet()

        val customRules = customGroups.flatMap { group ->
            group.rules.map { rule -> formatCustomRule(rule, group.name) }
        }

        val customGroupMaps = customGroups
            .filter { it.name !in baseGroupNames && !isBuiltinPolicy(it.name) }
            .map { group ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(group.name),
                        "type" to JsonPrimitive("select"),
                        "proxies" to JsonArray(
                            selectMembers(base, group.name, group.egress).map { JsonPrimitive(it) }
                        ),
                    )
                )
            }

        return MergedConfig(
            rules = customRules + base.rules,
            proxyGroups = base.proxyGroups.map { toJsonObject(it) } + customGroupMaps,
        )
    }

    suspend fun applyActive(context: Context) {
        applyLock.withLock {
            val store = ServiceStore(context)
            val profileUuid = store.activeProfile ?: return
            val base = parseBaseConfig(context, profileUuid) ?: run {
                Log.w("No base config available for $profileUuid, skip rule merge")

                return
            }

            val activeSet = store.activeRuleSet
                ?.let { RuleSetDao().queryByUUID(it) }
                ?.let { decodeRuleSetConfig(it) }

            val merged = buildMerged(base, activeSet)
            val override = Clash.queryOverride(Clash.OverrideSlot.Persist)

            if (merged == null) {
                if (override.rules == null && override.proxyGroups == null)
                    return

                override.rules = null
                override.proxyGroups = null
            } else {
                if (override.rules == merged.rules && override.proxyGroups == merged.proxyGroups)
                    return

                override.rules = merged.rules
                override.proxyGroups = merged.proxyGroups
            }

            Clash.patchOverride(Clash.OverrideSlot.Persist, override)

            context.sendOverrideChanged()
        }
    }

    suspend fun saveRuleSet(context: Context, config: RuleSetConfig) {
        val names = config.groups.map { it.name }
        if (names.size != names.toSet().size)
            throw IllegalArgumentException("Duplicate group name")

        val store = ServiceStore(context)
        val now = System.currentTimeMillis()
        val dao = RuleSetDao()
        val old = dao.queryByUUID(config.uuid)

        config.updatedAt = now

        dao.upsert(
            RuleSet(
                uuid = config.uuid,
                name = config.name,
                content = json.encodeToString(RuleSetConfig.serializer(), config),
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
        )

        if (store.activeRuleSet == config.uuid) {
            applyActive(context)
        }
    }

    suspend fun deleteRuleSet(context: Context, uuid: UUID) {
        val store = ServiceStore(context)

        RuleSetDao().remove(uuid)

        if (store.activeRuleSet == uuid) {
            store.activeRuleSet = null

            applyActive(context)
        }
    }

    suspend fun setActiveRuleSet(context: Context, uuid: UUID?) {
        ServiceStore(context).activeRuleSet = uuid

        applyActive(context)
    }

    suspend fun queryRulePreview(context: Context): List<RuleClassPreview> {
        val store = ServiceStore(context)
        val profileUuid = store.activeProfile ?: return emptyList()
        val base = parseBaseConfig(context, profileUuid) ?: return emptyList()
        val activeSet = store.activeRuleSet
            ?.let { RuleSetDao().queryByUUID(it) }
            ?.let { decodeRuleSetConfig(it) }

        val subscriptionRulesByGroup = LinkedHashMap<String, MutableList<String>>()
        for (rule in base.rules) {
            val target = ruleTarget(rule) ?: continue

            subscriptionRulesByGroup.getOrPut(target) { mutableListOf() }.add(rule)
        }

        val result = mutableListOf<RuleClassPreview>()

        for ((name, rules) in subscriptionRulesByGroup) {
            if (rules.isEmpty())
                continue

            result.add(
                RuleClassPreview(
                    name = name,
                    isCustom = false,
                    rules = rules.map { parseRuleLine(it) },
                )
            )
        }

        for (group in activeSet?.groups.orEmpty()) {
            val customLines = group.rules.mapIndexed { index, rule ->
                RulePreviewLine(
                    type = rule.type,
                    value = rule.value,
                    custom = true,
                    customIndex = index,
                )
            }
            val existing = result.indexOfFirst { it.name == group.name }
            if (existing >= 0) {
                val previous = result[existing]
                result[existing] = previous.copy(
                    isCustom = true,
                    rules = customLines + previous.rules,
                )
            } else {
                result.add(
                    RuleClassPreview(
                        name = group.name,
                        isCustom = true,
                        rules = customLines,
                    )
                )
            }
        }

        return result
    }

    fun decodeRuleSetConfig(ruleSet: RuleSet): RuleSetConfig? {
        return try {
            json.decodeFromString(RuleSetConfig.serializer(), ruleSet.content)
        } catch (e: Exception) {
            Log.w("Decode rule set ${ruleSet.uuid} failed: ${e.message}", e)

            null
        }
    }

    /** MATCH has no payload. Every other custom rule is `type,value,policy`. */
    private fun formatCustomRule(rule: CustomRule, policy: String): String {
        return if (rule.type.equals("MATCH", ignoreCase = true)) "MATCH,$policy"
        else "${rule.type},${rule.value},$policy"
    }

    private fun isBuiltinPolicy(name: String): Boolean {
        return name.uppercase() in builtinPolicies
    }

    /**
     * Members of a user-created select group: the main selector's options, plus DIRECT.
     * A stored egress is only the initial option when it is already one of those members.
     * Live group members are preferred so provider-backed selectors still list their nodes.
     */
    private fun selectMembers(base: BaseConfig, groupName: String, egress: String?): List<String> {
        val source = liveMembers(base.defaultGroup).ifEmpty { yamlMembers(base) }
        val withDirect = if (source.any { it.equals("DIRECT", true) }) source else source + "DIRECT"
        val filtered = withDirect
            .filter { it.isNotBlank() && !it.equals(groupName, true) }
            .distinct()
        val preferred = egress?.takeIf { name -> filtered.any { it == name } }
        val ordered = if (preferred == null) filtered else listOf(preferred) + filtered.filter { it != preferred }
        return ordered.ifEmpty { listOf("DIRECT") }
    }

    private fun yamlMembers(base: BaseConfig): List<String> {
        val name = base.defaultGroup ?: return emptyList()
        val group = base.proxyGroups.firstOrNull { it["name"] == name } ?: return emptyList()
        return (group["proxies"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    }

    private fun liveMembers(groupName: String?): List<String> {
        if (groupName.isNullOrBlank())
            return emptyList()

        return try {
            val group = Clash.queryGroup(groupName, ProxySort.Default)
            if (group.proxies.isEmpty()) emptyList()
            else group.proxies.map { it.name }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ruleTarget(rule: String): String? {
        val parts = rule.split(",").map { it.trim() }
        if (parts.isEmpty())
            return null

        return parts.getOrNull(2)
            ?: if (parts.size >= 2 && parts[0].equals("MATCH", true)) parts[1] else null
    }

    private fun toJsonObject(map: Map<String, Any?>): JsonObject {
        return JsonObject(map.mapValues { (_, value) -> toJsonElement(value) })
    }

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { toJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
