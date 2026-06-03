package de.eberhardt.unlockcapture.integrity

import java.io.InputStream
import java.security.MessageDigest

object Hashing {
    fun sha256Hex(input: InputStream): String {
        input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

