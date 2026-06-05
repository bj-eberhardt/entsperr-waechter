package de.eberhardt.unlockcapture.ui.history

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.audit.AuditLog
import de.eberhardt.unlockcapture.audit.AuditLogVerification
import de.eberhardt.unlockcapture.ui.components.adaptiveActionButtonWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AuditLog.Entry>>(emptyList()) }
    var verification by remember { mutableStateOf<AuditLogVerification>(AuditLogVerification.Empty) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { AuditLog.readAndVerify(context.applicationContext) }
                items = result.entries.filter { it.type == "UNLOCK" }.sortedByDescending { it.tsMs }
                verification = result.verification
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error = exception.message ?: exception.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { refresh() }, modifier = Modifier.adaptiveActionButtonWidth()) {
            Text(if (loading) stringResource(R.string.loading_ellipsis) else stringResource(R.string.action_refresh))
        }
    }

    if (verification is AuditLogVerification.Tampered) {
        Text(stringResource(R.string.history_tamper_warning), color = MaterialTheme.colorScheme.error)
    }

    if (error != null) {
        Text(stringResource(R.string.history_error_prefix, error ?: ""))
    }

    if (items.isEmpty() && !loading) {
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.history_empty_body))
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(items.size) { index ->
            val entry = items[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isFail = entry.result == "FAIL"
                    val iconRes =
                        if (isFail) android.R.drawable.ic_delete else android.R.drawable.checkbox_on_background
                    val tint =
                        if (isFail) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    Icon(painterResource(iconRes), contentDescription = null, tint = tint)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Text(historyMessage(context, entry), style = MaterialTheme.typography.bodyMedium)
                        Text(entry.isoTime, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun historyMessage(context: Context, entry: AuditLog.Entry): String = when (entry.eventKey) {
    AuditLog.EVENT_UNLOCK_FAILED -> context.getString(R.string.history_event_unlock_failed)
    AuditLog.EVENT_UNLOCK_SUCCESS -> context.getString(R.string.history_event_unlock_success)
    else -> entry.message
}
