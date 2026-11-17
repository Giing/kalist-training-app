package com.kalist.training.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kalist.training.R
import com.kalist.training.ui.theme.HugeNumber

/**
 * A +/- stepper. [compact] is used in forms; the large variant is used on the active session screen
 * (big numbers, 72 dp buttons for sweaty hands).
 */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999,
    step: Int = 1,
    suffix: String? = null,
    compact: Boolean = false,
    valueStyle: TextStyle? = null,
) {
    val buttonSize = if (compact) 48.dp else 72.dp
    val iconSize = if (compact) 24.dp else 40.dp
    val style = valueStyle ?: if (compact) MaterialTheme.typography.titleLarge else HugeNumber
    val display = if (suffix != null) "$value $suffix" else "$value"
    Row(
        modifier.semantics(mergeDescendants = false) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        val decreaseLabel = stringResource(R.string.cd_decrease, label)
        val increaseLabel = stringResource(R.string.cd_increase, label)
        FilledTonalIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(buttonSize).semantics { contentDescription = decreaseLabel },
        ) { Icon(Icons.Filled.Remove, null, Modifier.size(iconSize)) }
        Text(
            text = display,
            style = style,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = if (compact) 64.dp else 140.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = display
                },
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(buttonSize).semantics { contentDescription = increaseLabel },
        ) { Icon(Icons.Filled.Add, null, Modifier.size(iconSize)) }
    }
}
