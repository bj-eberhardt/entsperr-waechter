package de.eberhardt.unlockcapture.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.UnlockLoggingMode

@Composable
internal fun Modifier.adaptiveActionButtonWidth(): Modifier {
    val configuration = LocalConfiguration.current
    return if (configuration.screenWidthDp >= 600) {
        widthIn(min = 160.dp, max = 320.dp)
    } else {
        fillMaxWidth()
    }
}

@Composable
internal fun ModeRow(label: String, value: CaptureMode, selected: CaptureMode, onMode: (CaptureMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ModeRow(label: String, value: UnlockLoggingMode, selected: UnlockLoggingMode, onMode: (UnlockLoggingMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ModeRow(label: String, value: Long, selected: Long, onMode: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onMode(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SettingsGroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
internal fun RequirementCard(
    title: String,
    ok: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(
                    R.string.status_format,
                    title,
                    if (ok) stringResource(R.string.status_ok) else stringResource(R.string.status_missing),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(description)
            if (!ok) {
                Button(onClick = onAction, modifier = Modifier.adaptiveActionButtonWidth()) { Text(actionLabel) }
            }
        }
    }
}
