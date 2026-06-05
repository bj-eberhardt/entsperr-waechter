package de.eberhardt.unlockcapture.integrity

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

object IntegrityStore {
    private const val FILE_NAME = "integrity.jsonl"
    private val lock = Any()

    data class Record(
        val uri: String,
        val sha256: String,
        val sizeBytes: Long,
        val tsMs: Long,
    )

    fun upsert(
        context: Context,
        record: Record,
    ) {
        val appContext = context.applicationContext
        val file = File(appContext.filesDir, FILE_NAME)
        synchronized(lock) {
            val map = loadAllLocked(file).toMutableMap()
            map[record.uri] = record
            writeAllLocked(file, map.values.sortedBy { it.tsMs })
        }
    }

    fun get(
        context: Context,
        uri: String,
    ): Record? {
        val appContext = context.applicationContext
        val file = File(appContext.filesDir, FILE_NAME)
        synchronized(lock) {
            return loadAllLocked(file)[uri]
        }
    }

    fun list(context: Context): List<Record> {
        val appContext = context.applicationContext
        val file = File(appContext.filesDir, FILE_NAME)
        synchronized(lock) {
            return loadAllLocked(file).values.sortedByDescending { it.tsMs }
        }
    }

    private fun loadAllLocked(file: File): Map<String, Record> {
        if (!file.exists()) return emptyMap()
        val out = LinkedHashMap<String, Record>()
        file.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
            val uri = obj.optString("uri", "")
            val sha = obj.optString("sha256", "")
            val size = obj.optLong("sizeBytes", -1L)
            val ts = obj.optLong("tsMs", -1L)
            if (uri.isBlank() || sha.isBlank() || size < 0L || ts < 0L) return@forEachLine
            out[uri] = Record(uri = uri, sha256 = sha, sizeBytes = size, tsMs = ts)
        }
        return out
    }

    private fun writeAllLocked(
        file: File,
        records: List<Record>,
    ) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val sb = StringBuilder()
        for (r in records) {
            val obj =
                JSONObject().apply {
                    put("uri", r.uri)
                    put("sha256", r.sha256)
                    put("sizeBytes", r.sizeBytes)
                    put("tsMs", r.tsMs)
                }
            sb.append(obj.toString()).append('\n')
        }
        tmp.writeText(sb.toString(), Charsets.UTF_8)
        if (file.exists() && !file.delete()) {
            throw IOException("Failed to delete ${file.name}")
        }
        if (!tmp.renameTo(file)) {
            throw IOException("Failed to replace ${file.name}")
        }
    }
}
