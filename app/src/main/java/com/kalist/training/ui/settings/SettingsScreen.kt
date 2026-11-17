package com.kalist.training.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.settings.Accent
import com.kalist.training.data.settings.ThemeMode
import com.kalist.training.ui.components.NumberStepper
import com.kalist.training.ui.theme.isDynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    dataSection: @Composable () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            ThemeModeSelector(settings.themeMode, viewModel::setThemeMode)
            AccentSelector(settings.accent, viewModel::setAccent)

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_training))
            LabeledRow(stringResource(R.string.settings_weekly_goal)) {
                NumberStepper(
                    value = settings.weeklyGoal,
                    onValueChange = viewModel::setWeeklyGoal,
                    range = 1..14,
                    label = stringResource(R.string.settings_weekly_goal),
                    compact = true,
                )
            }
            LabeledRow(stringResource(R.string.settings_default_rest)) {
                NumberStepper(
                    value = settings.defaultRestSeconds,
                    onValueChange = viewModel::setDefaultRest,
                    range = 0..600,
                    step = 15,
                    label = stringResource(R.string.settings_default_rest),
                    suffix = stringResource(R.string.unit_seconds_short),
                    compact = true,
                )
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_language))
            LanguageSelector()

            dataSection()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.SYSTEM to R.string.theme_system,
    )
    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(stringResource(labels.getValue(mode))) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSelector(current: Accent, onSelect: (Accent) -> Unit) {
    val names = mapOf(
        Accent.ORANGE to R.string.accent_orange,
        Accent.TEAL to R.string.accent_teal,
        Accent.INDIGO to R.string.accent_indigo,
        Accent.GREEN to R.string.accent_green,
        Accent.PINK to R.string.accent_pink,
        Accent.DYNAMIC to R.string.accent_dynamic,
    )
    val accents = Accent.entries.filter { it != Accent.DYNAMIC || isDynamicColorSupported }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accents.forEach { accent ->
                val selected = accent == current
                val name = stringResource(names.getValue(accent))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (accent == Accent.DYNAMIC) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)
                            else Modifier.background(Color(accent.argb))
                        )
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable(role = Role.RadioButton) { onSelect(accent) }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        selected -> Icon(Icons.Filled.Check, null, tint = if (accent == Accent.DYNAMIC) MaterialTheme.colorScheme.onTertiaryContainer else Color.White)
                        accent == Accent.DYNAMIC -> Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector() {
    val options = listOf("" to R.string.language_system, "fr" to R.string.language_french, "en" to R.string.language_english)
    var current by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')) }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    current = tag
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}
