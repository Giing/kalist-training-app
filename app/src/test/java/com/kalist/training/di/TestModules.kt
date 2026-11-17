package com.kalist.training.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.kalist.training.TestClock
import com.kalist.training.data.db.ExerciseDao
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.SessionDao
import com.kalist.training.data.db.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Singleton

/** In-memory database (it outlives activities, like the file on disk) and a controllable clock. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KalistDatabase =
        Room.inMemoryDatabaseBuilder(context, KalistDatabase::class.java).build()

    @Provides fun provideExerciseDao(db: KalistDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideTemplateDao(db: KalistDatabase): TemplateDao = db.templateDao()
    @Provides fun provideSessionDao(db: KalistDatabase): SessionDao = db.sessionDao()

    @Provides @Singleton fun provideTestClock(): TestClock = TestClock()
    @Provides fun provideClock(clock: TestClock): Clock = clock

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** A fresh DataStore file per test. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {
    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") }
}
