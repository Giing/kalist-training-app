package com.kalist.training.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalist.training.data.settings.Accent
import com.kalist.training.data.settings.AppSettings
import com.kalist.training.data.settings.SettingsRepository
import com.kalist.training.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAccent(accent: Accent) = viewModelScope.launch { settingsRepository.setAccent(accent) }
    fun setWeeklyGoal(goal: Int) = viewModelScope.launch { settingsRepository.setWeeklyGoal(goal) }
    fun setDefaultRest(seconds: Int) = viewModelScope.launch { settingsRepository.setDefaultRestSeconds(seconds) }
}
