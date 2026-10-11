package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.common.util.createListFromParcelSlice
import com.github.kr328.clash.common.util.writeToParcelSlice
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * One tag in the rules browser. [name] is the Clash policy the rules target
 * (a proxy group, DIRECT, or REJECT). [rules] lists every rule under that tag,
 * user rules first. The group's selected node is not stored here; the UI reads
 * it from the loaded [ProxyGroup].
 */
@Serializable
data class RuleClassPreview(
    val name: String,
    val isCustom: Boolean,
    val rules: List<RulePreviewLine> = emptyList(),
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RuleClassPreview> {
        override fun createFromParcel(parcel: Parcel): RuleClassPreview {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<RuleClassPreview?> {
            return arrayOfNulls(size)
        }
    }
}

/** A rule row: match [type] and [value], plus trailing params such as `no-resolve` in [extra]. */
@Serializable
data class RulePreviewLine(
    val type: String,
    val value: String = "",
    val extra: String = "",
    val custom: Boolean = false,
    /** Index into the owning custom group's rule list; -1 for subscription rules. */
    val customIndex: Int = -1,
)

private val COMMA_PAYLOAD_TYPES = setOf(
    "NOT",
    "OR",
    "AND",
    "SUB-RULE",
    "DOMAIN-REGEX",
    "PROCESS-NAME-REGEX",
    "PROCESS-PATH-REGEX",
)

/**
 * Splits a Clash rule line into the parts the rules screen shows.
 * The policy (the tag) is dropped. Params after the policy, such as `no-resolve`, stay in [RulePreviewLine.extra].
 */
fun parseRuleLine(rule: String, custom: Boolean = false, customIndex: Int = -1): RulePreviewLine {
    val parts = rule.split(',').map { it.trim() }
    if (parts.isEmpty() || parts[0].isEmpty()) {
        return RulePreviewLine(rule.trim(), custom = custom, customIndex = customIndex)
    }

    val type = parts[0]
    if (type.equals("MATCH", ignoreCase = true)) {
        return RulePreviewLine(type, custom = custom, customIndex = customIndex)
    }

    if (type.uppercase() in COMMA_PAYLOAD_TYPES) {
        val payloadEnd = if (parts.size > 2) parts.lastIndex else parts.size
        val payload = parts.subList(1, payloadEnd).joinToString(",")
        return RulePreviewLine(type, payload, custom = custom, customIndex = customIndex)
    }

    val payload = parts.getOrNull(1).orEmpty()
    val extra = if (parts.size > 3) parts.subList(3, parts.size).joinToString(",") else ""
    return RulePreviewLine(type, payload, extra, custom, customIndex)
}

class RuleClassPreviewList(data: List<RuleClassPreview>) : List<RuleClassPreview> by data, Parcelable {
    constructor(parcel: Parcel) : this(RuleClassPreview.createListFromParcelSlice(parcel, 0, 20))

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        return writeToParcelSlice(parcel, flags)
    }

    companion object CREATOR : Parcelable.Creator<RuleClassPreviewList> {
        override fun createFromParcel(parcel: Parcel): RuleClassPreviewList {
            return RuleClassPreviewList(parcel)
        }

        override fun newArray(size: Int): Array<RuleClassPreviewList?> {
            return arrayOfNulls(size)
        }
    }
}
