package live.hisui.calorietracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import live.hisui.calorietracker.data.db.AppDatabase
import live.hisui.calorietracker.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
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

    private companion object {
        const val DB = "migration-test"
    }
}
