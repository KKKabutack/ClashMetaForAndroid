@file:UseSerializers(UUIDSerializer::class)

package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.common.util.createListFromParcelSlice
import com.github.kr328.clash.common.util.writeToParcelSlice
import com.github.kr328.clash.core.util.Parcelizer
import com.github.kr328.clash.core.util.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
data class RuleSetConfig(
    val uuid: UUID,
    var name: String,
    var groups: List<CustomRuleGroup> = emptyList(),
    var updatedAt: Long = 0,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RuleSetConfig> {
        override fun createFromParcel(parcel: Parcel): RuleSetConfig {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<RuleSetConfig?> {
            return arrayOfNulls(size)
        }
    }
}

@Serializable
data class CustomRuleGroup(
    val name: String,
    var egress: String? = null,
    var rules: MutableList<CustomRule> = mutableListOf(),
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CustomRuleGroup> {
        override fun createFromParcel(parcel: Parcel): CustomRuleGroup {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<CustomRuleGroup?> {
            return arrayOfNulls(size)
        }
    }
}

@Serializable
data class CustomRule(
    @SerialName("type") val type: String,
    @SerialName("value") val value: String,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CustomRule> {
        override fun createFromParcel(parcel: Parcel): CustomRule {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<CustomRule?> {
            return arrayOfNulls(size)
        }
    }
}

class RuleSetConfigList(data: List<RuleSetConfig>) : List<RuleSetConfig> by data, Parcelable {
    constructor(parcel: Parcel) : this(RuleSetConfig.createListFromParcelSlice(parcel, 0, 20))

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        return writeToParcelSlice(parcel, flags)
    }

    companion object CREATOR : Parcelable.Creator<RuleSetConfigList> {
        override fun createFromParcel(parcel: Parcel): RuleSetConfigList {
            return RuleSetConfigList(parcel)
        }

        override fun newArray(size: Int): Array<RuleSetConfigList?> {
            return arrayOfNulls(size)
        }
    }
}
