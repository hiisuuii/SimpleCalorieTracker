package live.hisui.calorietracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.MIGRATION_1_2
import live.hisui.calorietracker.data.db.MIGRATION_2_3
import live.hisui.calorietracker.data.db.MIGRATION_3_4
import live.hisui.calorietracker.data.db.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrationFrom1RemovesCodeOnlyPortionsAndKeepsRealOnes() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO food (id, name, source, kcal_per_100g, is_favorite) VALUES (1, 'Bun', 'USDA', 250, 0)")
            execSQL("INSERT INTO portion (food_id, label, grams) VALUES (1, '1 90000', 52), (1, '1 hamburger bun', 52), (1, '1 cup', 140), (1, '1 2 pieces', 30)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        val labels = db.query("SELECT label FROM portion ORDER BY label").use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        assertEquals(listOf("1 2 pieces", "1 cup", "1 hamburger bun"), labels)
    }

    @Test
    fun migrationFrom2AddsGroupColumnsAndKeepsEntries() {
        helper.createDatabase(DB_V2, 2).apply {
            execSQL("INSERT INTO log_entry (id, logged_at, tz_id, log_date, meal, name, kcal, source) VALUES (1, 0, 'UTC', '2026-10-20', 'LUNCH', 'Toast', 100, 'QUICK_ADD')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V2, 3, true, MIGRATION_2_3)

        db.query("SELECT name, group_id, group_name FROM log_entry").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Toast", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun migrationFrom3KeepsSavedMealsWithoutATotalWeight() {
        helper.createDatabase(DB_V3, 3).apply {
            execSQL("INSERT INTO saved_meal (id, name) VALUES (1, 'Sandwich')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V3, 4, true, MIGRATION_3_4)

        db.query("SELECT name, total_grams FROM saved_meal").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Sandwich", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrationFrom4KeepsSavedMealsWithoutAServingCount() {
        helper.createDatabase(DB_V4, 4).apply {
            execSQL("INSERT INTO saved_meal (id, name, total_grams) VALUES (1, 'Stew', 900)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V4, 5, true, MIGRATION_4_5)

        db.query("SELECT total_grams, total_servings FROM saved_meal").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(900.0, c.getDouble(0), 0.0)
            assertTrue(c.isNull(1))
        }
    }

    private companion object {
        const val DB_V4 = "migration-test-v4"
        const val DB_V3 = "migration-test-v3"
        const val DB = "migration-test"
        const val DB_V2 = "migration-test-v2"
    }
}
