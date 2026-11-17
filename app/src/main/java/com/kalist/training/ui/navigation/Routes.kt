package com.kalist.training.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object CalendarRoute
@Serializable object TemplatesRoute
@Serializable object ExercisesRoute
@Serializable object SettingsRoute

/** [id] = 0 creates a new exercise. */
@Serializable data class ExerciseEditRoute(val id: Long = 0)

/** [id] = 0 creates a new template. */
@Serializable data class TemplateEditRoute(val id: Long = 0)

@Serializable data class ActiveSessionRoute(val sessionId: Long)

@Serializable data class SessionDetailRoute(val sessionId: Long, val justFinished: Boolean = false)

@Serializable data class ExerciseHistoryRoute(val exerciseId: Long)
