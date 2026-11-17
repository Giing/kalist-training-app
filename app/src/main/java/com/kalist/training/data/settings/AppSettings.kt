package com.kalist.training.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent presets. [DYNAMIC] uses Material You colors on Android 12+ and falls back to [ORANGE]. */
enum class Accent(val argb: Long) {
    ORANGE(0xFFE8572A),
    TEAL(0xFF1F7A8C),
    INDIGO(0xFF4F5BD5),
    GREEN(0xFF2E8B57),
    PINK(0xFFD6336C),
    DYNAMIC(0xFFE8572A),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.ORANGE,
    val weeklyGoal: Int = DEFAULT_WEEKLY_GOAL,
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
) {
    companion object {
        const val DEFAULT_WEEKLY_GOAL = 3
        const val DEFAULT_REST_SECONDS = 90
    }
}
