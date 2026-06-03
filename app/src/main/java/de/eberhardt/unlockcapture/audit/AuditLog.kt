package de.eberhardt.unlockcapture.audit

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import de.eberhardt.unlockcapture.util.AppLog
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore
import java.security.SecureRandom

object AuditLog {
    private data class Record(
        val ts: Long,
        val type: String,
        val msg: String,
        val metaJson: String,
        val mac: String,
    )

    data class Entry(
        val tsMs: Long,
        val type: String,
        val message: String,
        val eventKey: String?,
        val result: String?,
        val isoTime: String,
    )

    data class ReadResult(
        val entries: List<Entry>,
        val verification: AuditLogVerification,
    )

    private const val KEY_ALIAS = "UnlockCaptureAuditHmac"
    private const val FILE_NAME = "audit.log"
    private const val TAMPER_FLAG_NAME = "audit.tampered"
    private const val RETENTION_MS = 14L * 24 * 60 * 60 * 1000
    const val EVENT_UNLOCK_FAILED = "unlock_failed"
    const val EVENT_UNLOCK_SUCCESS = "unlock_success"

    private val lock = Any()

    fun appendUnlockEvent(context: Context, eventKey: String, result: String) {
        append(
            context = context,
            type = "UNLOCK",
            message = "",
            meta = mapOf(
                "eventKey" to eventKey,
                "result" to result,
            )
        )
    }

    fun append(context: Context, type: String, message: String, meta: Map<String, String> = emptyMap()) {
        runCatching {
            val appContext = context.applicationContext
            val file = File(appContext.filesDir, FILE_NAME)
            val tamperFlag = File(appContext.filesDir, TAMPER_FLAG_NAME)
            val key = getOrCreateKey(appContext)

            synchronized(lock) {
                val now = System.currentTimeMillis()
                val cutoffMs = now - RETENTION_MS

                val verified = readVerifiedRecords(file, key)
                val existingRecords = when (verified) {
                    is VerifiedRecords.Ok -> verified.records
                    is VerifiedRecords.Tampered -> {
                        runCatching { tamperFlag.writeText("ts=$now reason=${verified.reason}", Charsets.UTF_8) }
                        rotateTampered(file, appContext.filesDir, now)
                        emptyList()
                    }
                }

                val retained = existingRecords.filter { it.ts >= cutoffMs }
                val rebuilt = rebuildChain(key, retained)
                writeAtomically(appContext.filesDir, file, rebuilt)

                val prev = rebuilt.lastOrNull()?.mac ?: ""
                val metaJson = JSONObject(meta as Map<*, *>).toString()
                val payload = canonicalPayload(now, type, message, metaJson, prev)
                val mac = hmacBase64(key, payload)
                val json = JSONObject().apply {
                    put("v", 1)
                    put("ts", now)
                    put("type", type)
                    put("msg", message)
                    put("meta", metaJson)
                    put("prev", prev)
                    put("mac", mac)
                }
                file.appendText(json.toString() + "\n", Charsets.UTF_8)
            }
        }.onFailure {
            AppLog.w("Audit", "Failed to append audit log entry", it)
        }
    }

    fun readAndVerify(context: Context): ReadResult {
        val appContext = context.applicationContext
        val file = File(appContext.filesDir, FILE_NAME)
        if (!file.exists()) return ReadResult(emptyList(), AuditLogVerification.Empty)

        val key = runCatching { getOrCreateKey(appContext) }.getOrNull()
            ?: return ReadResult(emptyList(), AuditLogVerification.Tampered(0, "Keystore key missing"))

        val tamperFlag = File(appContext.filesDir, TAMPER_FLAG_NAME)

        val entries = mutableListOf<Entry>()
        var expectedPrev = ""
        var lineNo = 0
        val timeFmt = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        )

        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrElse {
            return ReadResult(emptyList(), AuditLogVerification.Tampered(0, "Read failed"))
        }

        for (line in lines) {
            lineNo++
            if (line.isBlank()) continue

            val obj = runCatching { JSONObject(line) }.getOrNull()
                ?: return ReadResult(entries, AuditLogVerification.Tampered(lineNo, "Invalid JSON"))

            val ts = obj.optLong("ts", -1L)
            val type = obj.optString("type", "")
            val msg = obj.optString("msg", "")
            val metaJson = obj.optString("meta", "{}")
            val prev = obj.optString("prev", "")
            val mac = obj.optString("mac", "")

            if (ts <= 0L || type.isBlank() || mac.isBlank()) {
                return ReadResult(entries, AuditLogVerification.Tampered(lineNo, "Missing fields"))
            }
            if (prev != expectedPrev) {
                return ReadResult(entries, AuditLogVerification.Tampered(lineNo, "Broken chain"))
            }

            val payload = canonicalPayload(ts, type, msg, metaJson, prev)
            val expectedMac = hmacBase64(key, payload)
            if (mac != expectedMac) {
                return ReadResult(entries, AuditLogVerification.Tampered(lineNo, "MAC mismatch"))
            }

            entries.add(
                Entry(
                    tsMs = ts,
                    type = type,
                    message = msg,
                    eventKey = runCatching { JSONObject(metaJson).optString("eventKey").takeIf { it.isNotBlank() } }.getOrNull(),
                    result = runCatching { JSONObject(metaJson).optString("result").takeIf { it.isNotBlank() } }.getOrNull(),
                    isoTime = timeFmt.format(Date(ts)),
                )
            )
            expectedPrev = mac
        }

        if (tamperFlag.exists()) {
            return ReadResult(entries, AuditLogVerification.Tampered(0, "Tamper flag present"))
        }
        return ReadResult(entries, AuditLogVerification.Ok(entries.size))
    }

    private fun canonicalPayload(ts: Long, type: String, msg: String, metaJson: String, prev: String): ByteArray {
        return "$ts|$type|$msg|$metaJson|$prev".toByteArray(Charsets.UTF_8)
    }

    private sealed interface VerifiedRecords {
        data class Ok(val records: List<Record>) : VerifiedRecords
        data class Tampered(val reason: String) : VerifiedRecords
    }

    private fun readVerifiedRecords(file: File, key: SecretKey): VerifiedRecords {
        if (!file.exists()) return VerifiedRecords.Ok(emptyList())
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrElse {
            return VerifiedRecords.Tampered("Read failed")
        }

        val records = mutableListOf<Record>()
        var expectedPrev = ""
        var lineNo = 0
        for (line in lines) {
            lineNo++
            if (line.isBlank()) continue
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: return VerifiedRecords.Tampered("Invalid JSON at $lineNo")
            val ts = obj.optLong("ts", -1L)
            val type = obj.optString("type", "")
            val msg = obj.optString("msg", "")
            val metaJson = obj.optString("meta", "{}")
            val prev = obj.optString("prev", "")
            val mac = obj.optString("mac", "")
            if (ts <= 0L || type.isBlank() || mac.isBlank()) return VerifiedRecords.Tampered("Missing fields at $lineNo")
            if (prev != expectedPrev) return VerifiedRecords.Tampered("Broken chain at $lineNo")
            val payload = canonicalPayload(ts, type, msg, metaJson, prev)
            val expectedMac = hmacBase64(key, payload)
            if (mac != expectedMac) return VerifiedRecords.Tampered("MAC mismatch at $lineNo")
            records.add(Record(ts = ts, type = type, msg = msg, metaJson = metaJson, mac = mac))
            expectedPrev = mac
        }
        return VerifiedRecords.Ok(records)
    }

    private fun rebuildChain(key: SecretKey, records: List<Record>): List<Record> {
        val out = ArrayList<Record>(records.size)
        var prev = ""
        for (r in records) {
            val payload = canonicalPayload(r.ts, r.type, r.msg, r.metaJson, prev)
            val mac = hmacBase64(key, payload)
            out.add(r.copy(mac = mac))
            prev = mac
        }
        return out
    }

    private fun writeAtomically(dir: File, target: File, records: List<Record>) {
        val tmp = File(dir, "${target.name}.tmp")
        var prevMac = ""
        val sb = StringBuilder()
        for (r in records) {
            val obj = JSONObject().apply {
                put("v", 1)
                put("ts", r.ts)
                put("type", r.type)
                put("msg", r.msg)
                put("meta", r.metaJson)
                put("prev", prevMac)
                put("mac", r.mac)
            }
            sb.append(obj.toString()).append('\n')
            prevMac = r.mac
        }
        tmp.writeText(sb.toString(), Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            throw IOException("Failed to delete ${target.name}")
        }
        if (!tmp.renameTo(target)) {
            throw IOException("Failed to replace ${target.name}")
        }
    }

    private fun rotateTampered(file: File, dir: File, now: Long) {
        if (!file.exists()) return
        val rotated = File(dir, "audit.tampered.$now.log")
        runCatching { file.renameTo(rotated) }
    }

    private fun hmacBase64(key: SecretKey, payload: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val out = mac.doFinal(payload)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun getOrCreateKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Best-effort fallback for API 21/22: store a random HMAC key in internal storage.
            val file = File(context.filesDir, "$KEY_ALIAS.key")
            val raw = if (file.exists()) {
                Base64.decode(file.readText(Charsets.UTF_8), Base64.NO_WRAP)
            } else {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                file.writeText(Base64.encodeToString(bytes, Base64.NO_WRAP), Charsets.UTF_8)
                bytes
            }
            return SecretKeySpec(raw, "HmacSHA256")
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
