package live.hisui.calorietracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import live.hisui.calorietracker.domain.Meal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollapsedMealsTest {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun collapsedSectionsAreRememberedAcrossStoreInstances() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.cacheDir.resolve("collapsed-test-${System.nanoTime()}.preferences_pb")

        val firstLaunch = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, Json)
        assertEquals(emptySet<Meal>(), firstLaunch.collapsedMeals.first())
        firstLaunch.setMealCollapsed(Meal.LUNCH, true)
        firstLaunch.setMealCollapsed(Meal.SNACK, true)
        firstLaunch.setMealCollapsed(Meal.SNACK, false)
        scope.coroutineContext[Job]?.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + Job())
        val secondLaunch = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file }, Json)
        assertEquals(setOf(Meal.LUNCH), secondLaunch.collapsedMeals.first())
        secondScope.coroutineContext[Job]?.cancel()
    }
}
