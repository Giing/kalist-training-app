package com.kalist.training.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [Exercise::class, Template::class, TemplateExercise::class, Session::class, SessionExercise::class, SetLog::class],
    version = 1,
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
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
