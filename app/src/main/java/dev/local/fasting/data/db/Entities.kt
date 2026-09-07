package dev.local.fasting.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.Symptom
import dev.local.fasting.domain.WeightTag

@Entity(
    tableName = "fast_log",
    indices = [Index("start_epoch_millis"), Index("end_epoch_millis")],
)
data class FastLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    /** `null` while the fast is running. At most one row may hold null. */
    @ColumnInfo(name = "end_epoch_millis") val endEpochMillis: Long? = null,
    @ColumnInfo(name = "meal_composition") val mealComposition: MealComposition,
    /**
     * Fasting goal this fast was started under. Rows written before the goal feature existed were
     * migrated to `OPEN`, which is the honest value — they were run with no target.
     */
    @ColumnInfo(name = "goal", defaultValue = "'OPEN'") val goal: FastingGoal = FastingGoal.OPEN,
    @ColumnInfo(name = "note") val note: String = "",
)

@Entity(
    tableName = "weight_entry",
    indices = [Index("timestamp_epoch_millis")],
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    /** Always stored in kilograms; display units are a presentation concern. */
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "body_fat_percent") val bodyFatPercent: Double? = null,
    @ColumnInfo(name = "tag") val tag: WeightTag,
    @ColumnInfo(name = "note") val note: String = "",
)

@Entity(
    tableName = "symptom_log",
    indices = [Index("timestamp_epoch_millis"), Index("fast_id")],
)
data class SymptomLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    @ColumnInfo(name = "fast_id") val fastId: Long? = null,
    @ColumnInfo(name = "energy_level") val energyLevel: Int,
    @ColumnInfo(name = "clarity_level") val clarityLevel: Int,
    @ColumnInfo(name = "hunger_level") val hungerLevel: Int,
    @ColumnInfo(name = "symptoms") val symptoms: Set<Symptom> = emptySet(),
    @ColumnInfo(name = "note") val note: String = "",
)
