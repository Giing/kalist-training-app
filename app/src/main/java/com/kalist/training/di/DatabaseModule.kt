package com.kalist.training.di

import android.content.Context
import androidx.room.Room
import com.kalist.training.data.db.ExerciseDao
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.SessionDao
import com.kalist.training.data.db.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KalistDatabase =
        Room.databaseBuilder(context, KalistDatabase::class.java, KalistDatabase.NAME)
            .addMigrations(*KalistDatabase.MIGRATIONS)
            .build()

    @Provides fun provideExerciseDao(db: KalistDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideTemplateDao(db: KalistDatabase): TemplateDao = db.templateDao()
    @Provides fun provideSessionDao(db: KalistDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
