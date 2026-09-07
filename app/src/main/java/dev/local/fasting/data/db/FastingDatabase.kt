package dev.local.fasting.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FastLogEntity::class, WeightEntryEntity::class, SymptomLogEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FastingDatabase : RoomDatabase() {

    abstract fun fastDao(): FastDao

    abstract fun weightDao(): WeightDao

    abstract fun symptomDao(): SymptomDao

    companion object {
        private const val NAME = "fasting.db"

        /**
         * Adds the per-fast goal. Existing rows get `OPEN` rather than the app default: they were
         * logged before goals existed, so claiming they targeted 16-8 would be inventing history.
         *
         * Migrated rather than destroyed — a wipe would take a log the user cannot get back.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fast_log ADD COLUMN goal TEXT NOT NULL DEFAULT 'OPEN'")
            }
        }

        @Volatile
        private var instance: FastingDatabase? = null

        /** Single database instance shared by the UI, the widget and the alarm receivers. */
        fun get(context: Context): FastingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FastingDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
