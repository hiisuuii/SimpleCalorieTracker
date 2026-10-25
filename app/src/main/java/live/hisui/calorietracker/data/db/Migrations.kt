package live.hisui.calorietracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Early builds saved USDA's internal portion codes (e.g. "1 90000") as portion labels.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM portion WHERE label GLOB '1 [0-9]*' AND label NOT GLOB '*[^0-9 ]*'")
    }
}
