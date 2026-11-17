package com.kalist.training.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Exercise::class, Template::class, TemplateExercise::class, Session::class, SessionExercise::class, SetLog::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KalistDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "kalist.db"

        /**
         * Every schema change must bump the version and add a Migration here.
         * Destructive migration is never enabled: losing training history is not acceptable.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session_exercise ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
