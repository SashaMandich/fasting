package dev.local.fasting.data.db

import androidx.room.TypeConverter
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.Symptom
import dev.local.fasting.domain.WeightTag

/**
 * Enums are persisted by name rather than ordinal so reordering an enum can never silently
 * reinterpret existing rows. Unknown names fall back to a safe default.
 */
class Converters {

    @TypeConverter
    fun mealToString(value: MealComposition): String = value.name

    @TypeConverter
    fun stringToMeal(value: String): MealComposition =
        MealComposition.entries.firstOrNull { it.name == value } ?: MealComposition.DEFAULT

    @TypeConverter
    fun goalToString(value: FastingGoal): String = value.name

    @TypeConverter
    fun stringToGoal(value: String): FastingGoal =
        FastingGoal.entries.firstOrNull { it.name == value } ?: FastingGoal.OPEN

    @TypeConverter
    fun tagToString(value: WeightTag): String = value.name

    @TypeConverter
    fun stringToTag(value: String): WeightTag =
        WeightTag.entries.firstOrNull { it.name == value } ?: WeightTag.STANDARD

    @TypeConverter
    fun symptomsToString(value: Set<Symptom>): String = value.joinToString(SEPARATOR) { it.name }

    @TypeConverter
    fun stringToSymptoms(value: String): Set<Symptom> =
        if (value.isBlank()) {
            emptySet()
        } else {
            value.split(SEPARATOR)
                .mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }
                .toSet()
        }

    private companion object {
        const val SEPARATOR = ","
    }
}
