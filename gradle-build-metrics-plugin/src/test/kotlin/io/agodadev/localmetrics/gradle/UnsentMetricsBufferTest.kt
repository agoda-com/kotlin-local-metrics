package io.agodadev.localmetrics.gradle

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class UnsentMetricsBufferTest {
    @TempDir
    lateinit var bufferDirectory: Path

    @Test
    fun `saves one file per payload named by its id and reads it back`() {
        val buffer = buffer()

        buffer.save("11111111-2222-3333-4444-555555555555", """{"id":"a"}""")

        val entries = buffer.list()
        assertEquals(1, entries.size)
        assertEquals("11111111-2222-3333-4444-555555555555.json", entries.single().name)
        assertEquals("""{"id":"a"}""", buffer.read(entries.single()))
    }

    @Test
    fun `overwrites the entry when the same payload is saved twice`() {
        val buffer = buffer()

        buffer.save("payload-1", """{"attempt":1}""")
        buffer.save("payload-1", """{"attempt":2}""")

        val entries = buffer.list()
        assertEquals(1, entries.size)
        assertEquals("""{"attempt":2}""", buffer.read(entries.single()))
    }

    @Test
    fun `replaces path separators in the payload id so entries stay inside the buffer`() {
        val buffer = buffer()

        buffer.save("../../escape", """{"id":"a"}""")

        assertEquals(listOf("______escape.json"), buffer.list().map(File::getName))
    }

    @Test
    fun `deletes an entry`() {
        val buffer = buffer()
        buffer.save("payload-1", """{"id":"a"}""")
        val entry = buffer.list().single()

        buffer.delete(entry)

        assertTrue(buffer.list().isEmpty())
        assertFalse(entry.exists())
    }

    @Test
    fun `lists entries oldest first`() {
        val buffer = buffer()
        buffer.save("payload-1", """{"id":"1"}""")
        buffer.save("payload-2", """{"id":"2"}""")
        buffer.save("payload-3", """{"id":"3"}""")
        entry("payload-1").setLastModified(3_000)
        entry("payload-2").setLastModified(1_000)
        entry("payload-3").setLastModified(2_000)

        assertEquals(
            listOf("payload-2.json", "payload-3.json", "payload-1.json"),
            buffer.list().map(File::getName),
        )
    }

    @Test
    fun `prune keeps only the newest entries within the count cap`() {
        val buffer = buffer(maxEntries = 2)
        (1..4).forEach { index ->
            buffer.save("payload-$index", """{"id":"$index"}""")
            entry("payload-$index").setLastModified(NOW - (5 - index) * 1_000L)
        }

        buffer.prune(nowMillis = NOW)

        assertEquals(listOf("payload-3.json", "payload-4.json"), buffer.list().map(File::getName))
    }

    @Test
    fun `prune drops entries older than the age cap regardless of the count cap`() {
        val buffer = buffer(maxEntries = 10, maxAgeMillis = 60_000)
        buffer.save("stale", """{"id":"stale"}""")
        buffer.save("fresh", """{"id":"fresh"}""")
        entry("stale").setLastModified(NOW - 61_000)
        entry("fresh").setLastModified(NOW - 59_000)

        buffer.prune(nowMillis = NOW)

        assertEquals(listOf("fresh.json"), buffer.list().map(File::getName))
    }

    @Test
    fun `save enforces the count cap so the buffer cannot grow unbounded`() {
        val buffer = buffer(maxEntries = 3)

        (1..20).forEach { index ->
            buffer.save("payload-$index", """{"id":"$index"}""")
            entry("payload-$index").setLastModified(NOW - (100 - index) * 1_000L)
        }

        assertEquals(
            listOf("payload-18.json", "payload-19.json", "payload-20.json"),
            buffer.list().map(File::getName),
        )
    }

    @Test
    fun `reports no entries and never throws when the directory does not exist`() {
        val buffer = UnsentMetricsBuffer(bufferDirectory.resolve("absent").toFile())

        assertTrue(buffer.list().isEmpty())
        assertNull(buffer.read(bufferDirectory.resolve("absent/payload-1.json").toFile()))
        buffer.prune(nowMillis = NOW)
        buffer.delete(bufferDirectory.resolve("absent/payload-1.json").toFile())
    }

    private fun buffer(
        maxEntries: Int = 200,
        // Effectively disables the age cap, so tests that doctor timestamps to exercise the
        // count cap are not also at the mercy of the age cap applied by save().
        maxAgeMillis: Long = Long.MAX_VALUE / 2,
    ): UnsentMetricsBuffer = UnsentMetricsBuffer(
        directory = bufferDirectory.toFile(),
        maxEntries = maxEntries,
        maxAgeMillis = maxAgeMillis,
    )

    private fun entry(id: String): File = bufferDirectory.resolve("$id.json").toFile()

    private companion object {
        /** A fixed "now" keeps every cap assertion independent of wall-clock time. */
        const val NOW = 1_700_000_000_000L
    }
}
