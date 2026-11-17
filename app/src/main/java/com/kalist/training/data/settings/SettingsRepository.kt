package com.kalist.training.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME_MODE].toEnum(ThemeMode.SYSTEM),
            accent = prefs[ACCENT].toEnum(Accent.ORANGE),
            weeklyGoal = prefs[WEEKLY_GOAL] ?: AppSettings.DEFAULT_WEEKLY_GOAL,
            defaultRestSeconds = prefs[DEFAULT_REST] ?: AppSettings.DEFAULT_REST_SECONDS,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.name }
    suspend fun setAccent(accent: Accent) = dataStore.edit { it[ACCENT] = accent.name }
    suspend fun setWeeklyGoal(goal: Int) = dataStore.edit { it[WEEKLY_GOAL] = goal.coerceIn(1, 14) }
    suspend fun setDefaultRestSeconds(seconds: Int) =
        dataStore.edit { it[DEFAULT_REST] = seconds.coerceIn(0, 600) }

    suspend fun clear() = dataStore.edit { it.clear() }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val DEFAULT_REST = intPreferencesKey("default_rest_seconds")
    }
}
