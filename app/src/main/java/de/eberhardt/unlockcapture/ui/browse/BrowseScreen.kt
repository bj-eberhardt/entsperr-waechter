package de.eberhardt.unlockcapture.ui.browse

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.browse.CapturedArtifact
import de.eberhardt.unlockcapture.browse.MediaStoreBrowser
import de.eberhardt.unlockcapture.integrity.IntegrityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BrowseScreen(
    canBrowse: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val loaded = MediaStoreBrowser.load(context.applicationContext)
                val presentUris = loaded.map { it.uri.toString() }.toHashSet()
                val presentEntries = withContext(Dispatchers.IO) {
                    loaded.map { artifact ->
                        val missing = runCatching {
                            context.contentResolver.openInputStream(artifact.uri)?.use { true } ?: false
                        }.getOrDefault(false).not()
                        BrowseEntry.Present(artifact, missing)
                    }
                }

                val missingEntries = withContext(Dispatchers.IO) {
                    IntegrityStore.list(context.applicationContext).filter { record ->
                        if (presentUris.contains(record.uri)) return@filter false
                        val uri = runCatching { Uri.parse(record.uri) }.getOrNull() ?: return@filter true
                        runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }
                            .getOrDefault(false)
                            .not()
                    }.map { BrowseEntry.Missing(it) }
                }
                entries = (presentEntries + missingEntries).sortedByDescending { it.tsMs }
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(canBrowse) {
        if (canBrowse) refresh()
    }

    if (!canBrowse) {
        Text(stringResource(R.string.browse_need_permissions))
        Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_grant_permissions))
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { refresh() }, modifier = Modifier.weight(1f)) {
            Text(if (loading) stringResource(R.string.loading_ellipsis) else stringResource(R.string.action_refresh))
        }
    }

    if (error != null) {
        Text(stringResource(R.string.browse_error_prefix, error ?: ""))
    }

    if (entries.isEmpty() && !loading) {
        Text(stringResource(R.string.browse_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.browse_empty_body))
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(entries.size) { index ->
            when (val entry = entries[index]) {
                is BrowseEntry.Present -> PresentEntry(entry, dateFormat)
                is BrowseEntry.Missing -> MissingEntry(entry, dateFormat)
            }
        }
    }
}

@Composable
private fun PresentEntry(entry: BrowseEntry.Present, dateFormat: SimpleDateFormat) {
    val context = LocalContext.current
    val item = entry.item
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, if (item.type.name == "VIDEO") "video/*" else "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(viewIntent) }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtifactThumbnail(item = item)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(dateFormat.format(Date(item.dateAddedSeconds * 1000)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (item.type.name == "VIDEO") stringResource(R.string.artifact_type_video) else stringResource(R.string.artifact_type_photo),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entry.missing) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_dialog_alert),
                    contentDescription = stringResource(R.string.artifact_missing),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MissingEntry(entry: BrowseEntry.Missing, dateFormat: SimpleDateFormat) {
    val record = entry.record
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_dialog_alert),
                contentDescription = stringResource(R.string.artifact_missing),
                tint = MaterialTheme.colorScheme.error,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.artifact_missing), style = MaterialTheme.typography.titleMedium)
                Text(dateFormat.format(Date(record.tsMs)), style = MaterialTheme.typography.bodyMedium)
                Text(record.uri, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

private sealed class BrowseEntry(open val tsMs: Long) {
    data class Present(val item: CapturedArtifact, val missing: Boolean) : BrowseEntry(item.dateAddedSeconds * 1000L)
    data class Missing(val record: IntegrityStore.Record) : BrowseEntry(record.tsMs)
}

@Composable
private fun ArtifactThumbnail(item: CapturedArtifact) {
    val context = LocalContext.current
    val image = produceState<android.graphics.Bitmap?>(initialValue = null, key1 = item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(item.uri, Size(192, 192), null)
                } else {
                    @Suppress("DEPRECATION")
                    when (item.type.name) {
                        "VIDEO" -> MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            item.id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null,
                        )
                        else -> MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver,
                            item.id,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null,
                        )
                    }
                }
            }.getOrNull()
        }
    }.value

    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .height(64.dp)
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            painter = painterResource(if (item.type.name == "VIDEO") android.R.drawable.ic_media_play else android.R.drawable.ic_menu_report_image),
            contentDescription = null,
        )
    }
}
