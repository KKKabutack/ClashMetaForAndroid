package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import java.util.*

@Dao
@TypeConverters(Converters::class)
interface RuleSetDao {
    @Query("SELECT * FROM rule_sets ORDER BY createdAt")
    suspend fun queryAll(): List<RuleSet>

    @Query("SELECT * FROM rule_sets WHERE uuid = :uuid")
    suspend fun queryByUUID(uuid: UUID): RuleSet?

    @Upsert
    suspend fun upsert(ruleSet: RuleSet)

    @Query("DELETE FROM rule_sets WHERE uuid = :uuid")
    suspend fun remove(uuid: UUID)
}
