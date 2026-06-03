package de.eberhardt.unlockcapture.capture

import android.content.Context
import android.net.Uri
import de.eberhardt.unlockcapture.integrity.Hashing
import de.eberhardt.unlockcapture.integrity.IntegrityStore
import de.eberhardt.unlockcapture.util.AppLog

object CaptureIntegrityRecorder {
    fun record(context: Context, uri: Uri, label: String) {
        val appContext = context.applicationContext
        runCatching {
            val stream = appContext.contentResolver.openInputStream(uri) ?: return@runCatching
            val sha = Hashing.sha256Hex(stream)
            val size = runCatching {
                appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
            IntegrityStore.upsert(
                appContext,
                IntegrityStore.Record(
                    uri = uri.toString(),
                    sha256 = sha,
                    sizeBytes = size,
                    tsMs = System.currentTimeMillis()
                )
            )
        }.onFailure {
            AppLog.w("Integrity", "Failed to hash $label", it)
        }
    }
}
