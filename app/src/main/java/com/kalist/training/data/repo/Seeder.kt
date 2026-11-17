package com.kalist.training.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.kalist.training.R
import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateExercise
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Inserts the spec's seed exercises and templates A and B on first launch (in the current UI language). */
@Singleton
class Seeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: KalistDatabase,
    private val dataStore: DataStore<Preferences>,
) {
    private val mutex = Mutex()

    suspend fun seedIfNeeded() = mutex.withLock {
        if (dataStore.data.first()[SEEDED] == true) return@withLock
        if (db.exerciseDao().count() == 0 && db.templateDao().count() == 0) seed()
        dataStore.edit { it[SEEDED] = true }
    }

    /** Called after a reset: the next [seedIfNeeded] seeds again. */
    suspend fun markUnseeded() = dataStore.edit { it.remove(SEEDED) }

    private suspend fun seed() = db.withTransaction {
        fun s(id: Int): String = context.getString(id)
        val now = Instant.now()
        fun ex(name: Int, type: TargetType, perSide: Boolean, eq: Equipment, easier: Int, harder: Int) =
            Exercise(name = s(name), targetType = type, perSide = perSide, equipment = eq,
                easierVariant = s(easier), harderVariant = s(harder), createdAt = now)

        val exercises = listOf(
            ex(R.string.seed_pushups, TargetType.REPS, false, Equipment.NONE, R.string.seed_pushups_easier, R.string.seed_pushups_harder),
            ex(R.string.seed_rows, TargetType.REPS, false, Equipment.TABLE, R.string.seed_rows_easier, R.string.seed_rows_harder),
            ex(R.string.seed_dips, TargetType.REPS, false, Equipment.CHAIR_BENCH, R.string.seed_dips_easier, R.string.seed_dips_harder),
            ex(R.string.seed_lunges, TargetType.REPS, true, Equipment.NONE, R.string.seed_lunges_easier, R.string.seed_lunges_harder),
            ex(R.string.seed_plank, TargetType.TIME, false, Equipment.NONE, R.string.seed_plank_easier, R.string.seed_plank_harder),
            ex(R.string.seed_pullups, TargetType.REPS, false, Equipment.BAR, R.string.seed_pullups_easier, R.string.seed_pullups_harder),
            ex(R.string.seed_pike, TargetType.REPS, false, Equipment.NONE, R.string.seed_pike_easier, R.string.seed_pike_harder),
            ex(R.string.seed_diamond, TargetType.REPS, false, Equipment.NONE, R.string.seed_diamond_easier, R.string.seed_diamond_harder),
            ex(R.string.seed_bulgarian, TargetType.REPS, true, Equipment.CHAIR_BENCH, R.string.seed_bulgarian_easier, R.string.seed_bulgarian_harder),
            ex(R.string.seed_hollow, TargetType.TIME, false, Equipment.NONE, R.string.seed_hollow_easier, R.string.seed_hollow_harder),
            ex(R.string.seed_side_plank, TargetType.TIME, true, Equipment.NONE, R.string.seed_side_plank_easier, R.string.seed_side_plank_harder),
        )
        val ids = db.exerciseDao().insertAll(exercises)
        val (pushups, rows, dips, lunges, plank) = ids.subList(0, 5)
        val (pullups, pike, diamond, bulgarian, hollow) = ids.subList(5, 10)
        val sidePlank = ids[10]

        fun item(exerciseId: Long, min: Int, max: Int, note: String? = null) =
            TemplateExercise(templateId = 0, exerciseId = exerciseId, position = 0, targetSets = 3, targetMin = min, targetMax = max, note = note)

        val templateDao = db.templateDao()
        templateDao.save(
            Template(name = s(R.string.seed_template_a), colorArgb = 0xFFE8572A.toInt(), rounds = 3, restSeconds = 90, createdAt = now),
            listOf(item(pushups, 6, 12), item(rows, 6, 10), item(dips, 8, 12), item(lunges, 8, 8), item(plank, 30, 45)),
        )
        templateDao.save(
            Template(name = s(R.string.seed_template_b), colorArgb = 0xFF1F7A8C.toInt(), rounds = 3, restSeconds = 90, createdAt = now.plusMillis(1)),
            listOf(
                item(pullups, 1, 10, s(R.string.seed_pullups_note)),
                item(pike, 6, 10), item(diamond, 6, 10), item(bulgarian, 8, 8), item(hollow, 20, 30), item(sidePlank, 20, 30),
            ),
        )
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("seeded_v1")
    }
}
