package live.hisui.calorietracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import live.hisui.calorietracker.domain.MealParser
import live.hisui.calorietracker.domain.RuleBasedParser

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun mealParser(): MealParser = RuleBasedParser()
}
