package com.github.kr328.clash.service.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.*

@Entity(tableName = "rule_sets")
@TypeConverters(Converters::class)
data class RuleSet(
    @PrimaryKey val uuid: UUID,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
