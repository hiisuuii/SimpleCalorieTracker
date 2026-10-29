package live.hisui.calorietracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Early builds saved USDA's internal portion codes (e.g. "1 90000") as portion labels.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM portion WHERE label GLOB '1 [0-9]*' AND label NOT GLOB '*[^0-9 ]*'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE log_entry ADD COLUMN group_id TEXT")
        db.execSQL("ALTER TABLE log_entry ADD COLUMN group_name TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_meal ADD COLUMN total_grams REAL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_meal ADD COLUMN total_servings REAL")
    }
}
