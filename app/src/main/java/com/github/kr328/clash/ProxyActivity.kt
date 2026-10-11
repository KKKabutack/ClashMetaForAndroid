package com.github.kr328.clash

import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.CustomRule
import com.github.kr328.clash.core.model.CustomRuleGroup
import com.github.kr328.clash.core.model.RuleSetConfig
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.compose.ClashTheme
import com.github.kr328.clash.design.compose.ProxyAction
import com.github.kr328.clash.design.compose.ProxyScreen
import com.github.kr328.clash.design.compose.ProxySegment
import com.github.kr328.clash.design.compose.ProxyUiState
import com.github.kr328.clash.design.compose.RuleEditorState
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import java.util.UUID

/** Compose selector screen used both before and after the VPN connection starts. */
class ProxyActivity : BaseActivity<Design<*>>() {
    private val uiState = MutableStateFlow(ProxyUiState())
    private val actions = Channel<ProxyAction>(Channel.UNLIMITED)

    override suspend fun main() {
        setContent {
            val state by uiState.collectAsState()
            ClashTheme {
                ProxyScreen(state = state, onAction = actions::trySend)
            }
        }

        ensureProfileLoaded()
        reloadGroups()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> reloadGroups()
                        // The core is reset on VPN stop and recreated services; reload
                        // the profile so selections keep working instead of hitting a
                        // stale core (which would silently drop stored selections).
                        Event.ClashStop, Event.ServiceRecreated, Event.ProfileChanged -> {
                            ensureProfileLoaded()
                            reloadGroups()
                        }
                        else -> Unit
                    }
                }
                actions.onReceive(::handleAction)
            }
        }
    }

    private suspend fun handleAction(action: ProxyAction) {
        when (action) {
            ProxyAction.Back -> onBackInvoked()
            is ProxyAction.SelectGroup -> uiState.update { it.copy(selectedGroup = action.name) }
            is ProxyAction.SelectProxy -> {
                withClash { patchSelector(action.group, action.proxy.name) }
                reloadGroups(action.group)
            }
            is ProxyAction.TestGroup -> {
                uiState.update { it.copy(testingGroup = action.group) }
                try {
                    withClash { healthCheck(action.group) }
                } finally {
                    reloadGroups(action.group)
                    uiState.update { it.copy(testingGroup = null) }
                }
            }

            is ProxyAction.SwitchSegment -> {
                uiState.update { it.copy(segment = action.segment) }
                if (action.segment == ProxySegment.Rules) reloadRulePreview()
            }
            is ProxyAction.OpenRuleClass -> {
                uiState.update { it.copy(selectedRuleClass = action.name) }
                reloadRulePreview()
            }
            ProxyAction.CloseRuleClass -> uiState.update { it.copy(selectedRuleClass = null) }
            ProxyAction.OpenRuleSets -> {
                reloadRuleSets()
                uiState.update { it.copy(showRuleSets = true) }
            }
            ProxyAction.CloseRuleSets -> uiState.update { it.copy(showRuleSets = false) }
            is ProxyAction.CreateRuleClass -> guardRuleCommand {
                mutateActiveRuleSet { config ->
                    if (config.groups.any { it.name == action.name } ||
                        uiState.value.ruleClasses.any { it.name == action.name }
                    ) {
                        throw IllegalArgumentException(getString(DesignR.string.rule_tag_duplicate))
                    }
                    config.copy(groups = config.groups + CustomRuleGroup(action.name))
                }
            }
            is ProxyAction.AddRule -> guardRuleCommand {
                mutateActiveRuleSet { config ->
                    val rule = CustomRule(action.type, action.normalizedValue())
                    if (config.groups.none { it.name == action.className }) {
                        config.copy(
                            groups = config.groups + CustomRuleGroup(
                                name = action.className,
                                rules = mutableListOf(rule),
                            )
                        )
                    } else {
                        config.copy(groups = config.groups.map {
                            if (it.name == action.className) {
                                it.copy(rules = (it.rules + rule).toMutableList())
                            } else {
                                it
                            }
                        })
                    }
                }
            }
            is ProxyAction.UpdateRule -> guardRuleCommand {
                mutateActiveRuleSet { config ->
                    config.copy(groups = config.groups.map {
                        if (it.name == action.className && action.index in it.rules.indices) {
                            it.copy(rules = it.rules.toMutableList().apply {
                                set(action.index, CustomRule(action.type, action.normalizedValue()))
                            })
                        } else {
                            it
                        }
                    })
                }
            }
            is ProxyAction.DeleteRule -> guardRuleCommand {
                mutateActiveRuleSet { config ->
                    config.copy(groups = config.groups.map {
                        if (it.name == action.className && action.index in it.rules.indices) {
                            it.copy(rules = it.rules.toMutableList().apply { removeAt(action.index) })
                        } else {
                            it
                        }
                    })
                }
            }
            is ProxyAction.DeleteRuleClass -> guardRuleCommand {
                val keepOpen = uiState.value.ruleClasses
                    .firstOrNull { it.name == action.className }
                    ?.rules
                    ?.any { !it.custom } == true
                mutateActiveRuleSet { config ->
                    config.copy(groups = config.groups.filterNot { it.name == action.className })
                }
                if (!keepOpen) {
                    uiState.update { it.copy(selectedRuleClass = null) }
                }
            }
            is ProxyAction.OpenRuleEditor -> uiState.update {
                it.copy(ruleEditor = RuleEditorState(action.className, action.index, action.initialType, action.initialValue))
            }
            ProxyAction.CloseRuleEditor -> uiState.update { it.copy(ruleEditor = null) }
            is ProxyAction.SaveRuleEditor -> {
                val editor = uiState.value.ruleEditor
                uiState.update { it.copy(ruleEditor = null) }
                if (editor != null) {
                    val index = editor.index
                    if (index == null) {
                        handleAction(ProxyAction.AddRule(editor.className, action.type, action.value))
                    } else {
                        handleAction(ProxyAction.UpdateRule(editor.className, index, action.type, action.value))
                    }
                }
            }

            is ProxyAction.ActivateRuleSet -> guardRuleCommand {
                withProfile { setActiveRuleSet(action.uuid) }
                reloadRuleSets()
                reloadRulePreview()
            }
            is ProxyAction.DuplicateRuleSet -> guardRuleCommand {
                val source = uiState.value.ruleSets.firstOrNull { it.uuid == action.uuid }
                if (source != null) {
                    withProfile {
                        saveRuleSet(source.copy(
                            uuid = UUID.randomUUID(),
                            name = getString(DesignR.string.rule_set_copy_format, source.name),
                            updatedAt = System.currentTimeMillis(),
                        ))
                    }
                    reloadRuleSets()
                }
            }
            is ProxyAction.RenameRuleSet -> guardRuleCommand {
                val source = uiState.value.ruleSets.firstOrNull { it.uuid == action.uuid }
                if (source != null) {
                    withProfile {
                        saveRuleSet(source.copy(name = action.name, updatedAt = System.currentTimeMillis()))
                    }
                    reloadRuleSets()
                }
            }
            is ProxyAction.DeleteRuleSet -> guardRuleCommand {
                withProfile { deleteRuleSet(action.uuid) }
                reloadRuleSets()
                reloadRulePreview()
            }
            is ProxyAction.CreateRuleSet -> guardRuleCommand {
                withProfile {
                    saveRuleSet(RuleSetConfig(
                        uuid = UUID.randomUUID(),
                        name = action.name,
                        updatedAt = System.currentTimeMillis(),
                    ))
                }
                reloadRuleSets()
            }
        }
    }

    /** MATCH rules carry no value; everything else stores the trimmed input verbatim. */
    private fun ProxyAction.AddRule.normalizedValue(): String =
        if (type == "MATCH") "" else value.trim()

    private fun ProxyAction.UpdateRule.normalizedValue(): String =
        if (type == "MATCH") "" else value.trim()

    /**
     * Loads the active rule set, creating and activating a default one when none
     * exists, then applies [block], persists the result and refreshes the preview.
     */
    private suspend fun mutateActiveRuleSet(block: (RuleSetConfig) -> RuleSetConfig) {
        val config = ensureActiveRuleSet()
        // Rebuild the mutable rule lists so failed or repeated mutations never
        // alias the lists the binder already parcelized.
        val rebuilt = config.copy(
            groups = config.groups.map { it.copy(rules = it.rules.toMutableList()) },
            updatedAt = System.currentTimeMillis(),
        )
        withProfile { saveRuleSet(block(rebuilt)) }
        // Disconnected sessions load the profile once; reload so a new select group
        // exists before the tag's node picker calls patchSelector. A running VPN
        // reloads itself from the override-changed broadcast.
        if (!clashRunning) {
            try {
                withClash { loadActiveProfile() }
            } catch (_: Exception) {
                // The rule set is already saved. The next profile load applies it.
            }
        }
        reloadRulePreview()
        reloadGroups()
    }

    private suspend fun ensureActiveRuleSet(): RuleSetConfig {
        withProfile { queryActiveRuleSet() }?.let { return it }

        val created = RuleSetConfig(
            uuid = UUID.randomUUID(),
            name = getString(DesignR.string.rule_set_default_name),
            updatedAt = System.currentTimeMillis(),
        )
        withProfile {
            saveRuleSet(created)
            setActiveRuleSet(created.uuid)
        }
        return created
    }

    private suspend fun guardRuleCommand(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Binder failures (e.g. duplicate group name) surface as a toast.
            e.message?.let(::toast)
        }
    }

    private suspend fun reloadRulePreview() {
        try {
            val preview = withProfile { queryRulePreview() }
            uiState.update { it.copy(ruleClasses = preview.toList()) }
        } catch (e: Exception) {
            toast(e.message ?: return)
        }
    }

    private suspend fun reloadRuleSets() {
        try {
            val sets = withProfile { queryRuleSets() }
            val active = withProfile { queryActiveRuleSet() }
            uiState.update { it.copy(ruleSets = sets.toList(), activeRuleSet = active?.uuid) }
        } catch (e: Exception) {
            toast(e.message ?: return)
        }
    }

    /**
     * Ensures the active profile is loaded into the core when the VPN is not running.
     * This lets latency tests work even while disconnected.
     */
    private suspend fun ensureProfileLoaded() {
        if (clashRunning) return

        val active = try {
            withProfile { queryActive() }
        } catch (e: Exception) {
            null
        }

        if (active == null || !active.imported) {
            toast(DesignR.string.no_profile_selected)
            finish()
            return
        }

        try {
            withClash { loadActiveProfile() }
        } catch (e: Exception) {
            toast(e.message ?: getString(DesignR.string.no_profile_selected))
            finish()
        }
    }

    private suspend fun reloadGroups(preferredGroup: String? = uiState.value.selectedGroup) {
        val groups = withClash {
            queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                .associateWith { name -> queryProxyGroup(name, uiStore.proxySort) }
        }
        uiState.update {
            it.copy(
                groups = groups,
                selectedGroup = preferredGroup?.takeIf(groups::containsKey) ?: groups.keys.firstOrNull(),
                loading = false,
            )
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onBackInvoked() {
        val state = uiState.value
        when {
            state.ruleEditor != null -> uiState.update { it.copy(ruleEditor = null) }
            state.showRuleSets -> uiState.update { it.copy(showRuleSets = false) }
            state.selectedRuleClass != null -> uiState.update { it.copy(selectedRuleClass = null) }
            else -> finish()
        }
    }

    companion object {
        val intent = ProxyActivity::class.intent
    }
}
