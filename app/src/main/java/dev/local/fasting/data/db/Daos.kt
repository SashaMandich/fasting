package dev.local.fasting.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FastDao {

    @Query("SELECT * FROM fast_log ORDER BY start_epoch_millis DESC")
    fun observeAll(): Flow<List<FastLogEntity>>

    @Query("SELECT * FROM fast_log WHERE end_epoch_millis IS NULL ORDER BY start_epoch_millis DESC LIMIT 1")
    fun observeActive(): Flow<FastLogEntity?>

    @Query("SELECT * FROM fast_log WHERE end_epoch_millis IS NULL ORDER BY start_epoch_millis DESC LIMIT 1")
    suspend fun activeFast(): FastLogEntity?

    @Query("SELECT * FROM fast_log WHERE id = :id")
    suspend fun byId(id: Long): FastLogEntity?

    @Query("SELECT * FROM fast_log ORDER BY start_epoch_millis DESC")
    suspend fun all(): List<FastLogEntity>

    @Insert
    suspend fun insert(entity: FastLogEntity): Long

    @Update
    suspend fun update(entity: FastLogEntity)

    @Upsert
    suspend fun upsert(entity: FastLogEntity): Long

    @Delete
    suspend fun delete(entity: FastLogEntity)

    @Query("DELETE FROM fast_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM fast_log")
    suspend fun deleteAll()

    /**
     * Closes any fast left open other than [keepId]. Guards the "only one active fast" invariant
     * when a retroactive edit reopens a historical fast.
     */
    @Query(
        "UPDATE fast_log SET end_epoch_millis = :endEpochMillis " +
            "WHERE end_epoch_millis IS NULL AND id != :keepId"
    )
    suspend fun closeOtherOpenFasts(keepId: Long, endEpochMillis: Long)
}

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entry ORDER BY timestamp_epoch_millis ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY timestamp_epoch_millis DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entry WHERE id = :id")
    suspend fun byId(id: Long): WeightEntryEntity?

    @Upsert
    suspend fun upsert(entity: WeightEntryEntity): Long

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM weight_entry")
    suspend fun deleteAll()
}

@Dao
interface SymptomDao {

    @Query("SELECT * FROM symptom_log ORDER BY timestamp_epoch_millis DESC")
    fun observeAll(): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_log WHERE fast_id = :fastId ORDER BY timestamp_epoch_millis ASC")
    fun observeForFast(fastId: Long): Flow<List<SymptomLogEntity>>

    @Upsert
    suspend fun upsert(entity: SymptomLogEntity): Long

    @Query("DELETE FROM symptom_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM symptom_log")
    suspend fun deleteAll()
}
