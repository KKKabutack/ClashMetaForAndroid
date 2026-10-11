package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `rule_sets` (" +
                "`uuid` TEXT NOT NULL PRIMARY KEY, " +
                "`name` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)

val LEGACY_MIGRATION = ::migrationFromLegacy
