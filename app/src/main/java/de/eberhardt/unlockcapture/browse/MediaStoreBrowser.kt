package de.eberhardt.unlockcapture.browse

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ArtifactType { IMAGE, VIDEO }

data class CapturedArtifact(
    val type: ArtifactType,
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long,
)

object MediaStoreBrowser {
    suspend fun load(context: Context): List<CapturedArtifact> = withContext(Dispatchers.IO) {
        val images =
            query(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                type = ArtifactType.IMAGE,
                relativePathPrefix = "Pictures/UnlockCapture",
            )
        val videos =
            query(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                type = ArtifactType.VIDEO,
                relativePathPrefix = "Movies/UnlockCapture",
            )
        (images + videos).sortedByDescending { it.dateAddedSeconds }
    }

    private fun query(
        context: Context,
        collection: Uri,
        type: ArtifactType,
        relativePathPrefix: String,
    ): List<CapturedArtifact> {
        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.RELATIVE_PATH,
            )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$relativePathPrefix%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val results = mutableListOf<CapturedArtifact>()
        context.contentResolver
            .query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "(unknown)"
                    val dateAdded = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    results.add(
                        CapturedArtifact(
                            type = type,
                            id = id,
                            uri = uri,
                            displayName = name,
                            dateAddedSeconds = dateAdded,
                        ),
                    )
                }
            }
        return results
    }
}
