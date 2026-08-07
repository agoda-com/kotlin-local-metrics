package io.agodadev.localmetrics.gradle

import java.io.File

/**
 * A bounded, best-effort store-and-forward buffer for payloads that could not be sent.
 *
 * One file per payload, named by the payload id. Every operation swallows its errors: a
 * developer's build must never fail because a metrics file could not be written or read.
 */
internal class UnsentMetricsBuffer(
    private val directory: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) {
    fun save(id: String, json: String) {
        runCatching {
            directory.mkdirs()
            val entry = File(directory, "${fileName(id)}$EXTENSION")
            val temporary = File(directory, "${entry.name}$TEMPORARY_EXTENSION")
            temporary.writeText(json)
            if (!temporary.renameTo(entry)) {
                temporary.copyTo(entry, overwrite = true)
                temporary.delete()
            }
            prune()
        }
    }

    /** Buffered entries, oldest first, so a flush drains them in the order they failed. */
    fun list(): List<File> = runCatching {
        directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            .orEmpty()
            .sortedBy(File::lastModified)
    }.getOrDefault(emptyList())

    fun read(entry: File): String? = runCatching { entry.readText() }.getOrNull()

    fun delete(entry: File) {
        runCatching { entry.delete() }
    }

    /** Drops entries older than [maxAgeMillis], then the oldest entries beyond [maxEntries]. */
    fun prune(nowMillis: Long = System.currentTimeMillis()) {
        runCatching {
            val expiredBefore = nowMillis - maxAgeMillis
            val (expired, current) = list().partition { it.lastModified() < expiredBefore }
            expired.forEach(::delete)
            current.dropLast(maxEntries.coerceAtLeast(0)).forEach(::delete)
        }
    }

    private fun fileName(id: String): String {
        val sanitized = id
            .take(MAX_FILE_NAME_LENGTH)
            .map { character ->
                if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
            }
            .joinToString(separator = "")

        return sanitized.ifBlank { "payload" }
    }

    private companion object {
        const val EXTENSION = ".json"
        const val TEMPORARY_EXTENSION = ".tmp"
        const val MAX_FILE_NAME_LENGTH = 64
        const val DEFAULT_MAX_ENTRIES = 200
        const val DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}
