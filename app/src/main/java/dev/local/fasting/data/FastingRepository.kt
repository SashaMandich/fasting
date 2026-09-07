package dev.local.fasting.data

import androidx.room.withTransaction
import dev.local.fasting.data.db.FastingDatabase
import dev.local.fasting.data.db.toDomain
import dev.local.fasting.data.db.toEntity
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.SymptomLog
import dev.local.fasting.domain.WeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Single source of truth for logs. Everything is local: Room is the only store, and every read is
 * a [Flow] so the UI, the widget and the notification all observe the same state.
 */
class FastingRepository(private val db: FastingDatabase) {

    val fasts: Flow<List<FastRecord>> =
        db.fastDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    val activeFast: Flow<FastRecord?> =
        db.fastDao().observeActive().map { it?.toDomain() }

    val weights: Flow<List<WeightRecord>> =
        db.weightDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    val symptoms: Flow<List<SymptomLog>> =
        db.symptomDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun activeFastNow(): FastRecord? = db.fastDao().activeFast()?.toDomain()

    suspend fun fastById(id: Long): FastRecord? = db.fastDao().byId(id)?.toDomain()

    suspend fun allFastsNow(): List<FastRecord> = db.fastDao().all().map { it.toDomain() }

    /**
     * Starts a fast, ending any fast still open first so the "one active fast" invariant holds even
     * if a previous fast was never closed.
     */
    suspend fun startFast(
        meal: MealComposition,
        goal: FastingGoal,
        start: Instant = Instant.now(),
    ): Long {
        db.fastDao().activeFast()?.let { open ->
            val end = maxOf(start, Instant.ofEpochMilli(open.startEpochMillis))
            db.fastDao().update(open.copy(endEpochMillis = end.toEpochMilli()))
        }
        return db.fastDao().insert(
            FastRecord(start = start, meal = meal, goal = goal).toEntity().copy(id = 0L)
        )
    }

    /** Ends the running fast. Returns the closed record, or null if nothing was running. */
    suspend fun endActiveFast(end: Instant = Instant.now()): FastRecord? {
        val open = db.fastDao().activeFast() ?: return null
        val safeEnd = maxOf(end, Instant.ofEpochMilli(open.startEpochMillis))
        val closed = open.copy(endEpochMillis = safeEnd.toEpochMilli())
        db.fastDao().update(closed)
        return closed.toDomain()
    }

    /**
     * Creates or retroactively edits a fast. Both timestamps are free to move; an edit that reopens
     * a past fast closes any other open fast so only one can ever be live.
     */
    suspend fun saveFast(record: FastRecord): Long {
        require(record.end == null || !record.end.isBefore(record.start)) {
            "A fast cannot end before it starts"
        }
        val id = db.fastDao().upsert(record.toEntity())
        val savedId = if (record.id != 0L) record.id else id
        if (record.end == null) {
            db.fastDao().closeOtherOpenFasts(
                keepId = savedId,
                endEpochMillis = Instant.now().toEpochMilli(),
            )
        }
        return savedId
    }

    suspend fun deleteFast(id: Long) = db.fastDao().deleteById(id)

    suspend fun saveWeight(record: WeightRecord): Long {
        require(record.weightKg > 0.0) { "Weight must be greater than zero" }
        require(record.bodyFatPercent == null || record.bodyFatPercent in 1.0..75.0) {
            "Body fat % looks out of range"
        }
        return db.weightDao().upsert(record.toEntity())
    }

    suspend fun deleteWeight(id: Long) = db.weightDao().deleteById(id)

    suspend fun saveSymptomLog(log: SymptomLog): Long {
        require(log.energyLevel in SymptomLog.LEVEL_RANGE) { "Energy must be 1–5" }
        require(log.clarityLevel in SymptomLog.LEVEL_RANGE) { "Clarity must be 1–5" }
        require(log.hungerLevel in SymptomLog.LEVEL_RANGE) { "Hunger must be 1–5" }
        return db.symptomDao().upsert(log.toEntity())
    }

    suspend fun deleteSymptomLog(id: Long) = db.symptomDao().deleteById(id)

    /**
     * Wipes every log — fasts, weights and symptom entries — in one transaction. There is no cloud
     * copy to restore from, so the UI gates this behind two separate confirmations.
     */
    suspend fun clearAllHistory() {
        db.withTransaction {
            db.symptomDao().deleteAll()
            db.weightDao().deleteAll()
            db.fastDao().deleteAll()
        }
    }
}
