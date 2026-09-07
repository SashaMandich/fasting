package dev.local.fasting.data.db

import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.SymptomLog
import dev.local.fasting.domain.WeightRecord
import java.time.Instant

fun FastLogEntity.toDomain(): FastRecord = FastRecord(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis),
    end = endEpochMillis?.let(Instant::ofEpochMilli),
    meal = mealComposition,
    goal = goal,
    note = note,
)

fun FastRecord.toEntity(): FastLogEntity = FastLogEntity(
    id = id,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end?.toEpochMilli(),
    mealComposition = meal,
    goal = goal,
    note = note,
)

fun WeightEntryEntity.toDomain(): WeightRecord = WeightRecord(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    tag = tag,
    note = note,
)

fun WeightRecord.toEntity(): WeightEntryEntity = WeightEntryEntity(
    id = id,
    timestampEpochMillis = timestamp.toEpochMilli(),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    tag = tag,
    note = note,
)

fun SymptomLogEntity.toDomain(): SymptomLog = SymptomLog(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    fastId = fastId,
    energyLevel = energyLevel,
    clarityLevel = clarityLevel,
    hungerLevel = hungerLevel,
    symptoms = symptoms,
    note = note,
)

fun SymptomLog.toEntity(): SymptomLogEntity = SymptomLogEntity(
    id = id,
    timestampEpochMillis = timestamp.toEpochMilli(),
    fastId = fastId,
    energyLevel = energyLevel,
    clarityLevel = clarityLevel,
    hungerLevel = hungerLevel,
    symptoms = symptoms,
    note = note,
)
