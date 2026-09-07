package dev.local.fasting.data

import android.content.Context
import androidx.core.content.edit
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.WeightTrend
import dev.local.fasting.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultMeal: MealComposition = MealComposition.DEFAULT,
    val defaultGoal: FastingGoal = FastingGoal.DEFAULT,
    val liveNotificationEnabled: Boolean = true,
    val emaPeriodDays: Int = WeightTrend.DEFAULT_PERIOD_DAYS,
)

/**
 * Preferences live in SharedPreferences rather than the database: they are tiny, need synchronous
 * reads from broadcast receivers, and are not part of the logged history.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Synchronous snapshot, for receivers and widget callbacks that cannot collect a flow. */
    fun current(): AppSettings = _settings.value

    fun setWeightUnit(unit: WeightUnit) = update { putString(KEY_UNIT, unit.name) }

    fun setDefaultMeal(meal: MealComposition) = update { putString(KEY_MEAL, meal.name) }

    fun setDefaultGoal(goal: FastingGoal) = update { putString(KEY_GOAL, goal.name) }

    fun setLiveNotificationEnabled(enabled: Boolean) = update { putBoolean(KEY_NOTIFY, enabled) }

    fun setEmaPeriodDays(days: Int) = update { putInt(KEY_EMA, days.coerceIn(2, 30)) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit { block() }
        _settings.value = read()
    }

    private fun read(): AppSettings {
        val default = AppSettings()
        return AppSettings(
            weightUnit = prefs.getString(KEY_UNIT, null)
                ?.let { name -> WeightUnit.entries.firstOrNull { it.name == name } }
                ?: default.weightUnit,
            defaultMeal = prefs.getString(KEY_MEAL, null)
                ?.let { name -> MealComposition.entries.firstOrNull { it.name == name } }
                ?: default.defaultMeal,
            defaultGoal = prefs.getString(KEY_GOAL, null)
                ?.let { name -> FastingGoal.entries.firstOrNull { it.name == name } }
                ?: default.defaultGoal,
            liveNotificationEnabled = prefs.getBoolean(KEY_NOTIFY, default.liveNotificationEnabled),
            emaPeriodDays = prefs.getInt(KEY_EMA, default.emaPeriodDays),
        )
    }

    private companion object {
        const val FILE = "fasting_settings"
        const val KEY_UNIT = "weight_unit"
        const val KEY_MEAL = "default_meal"
        const val KEY_GOAL = "default_goal"
        const val KEY_NOTIFY = "live_notification"
        const val KEY_EMA = "ema_period_days"
    }
}
