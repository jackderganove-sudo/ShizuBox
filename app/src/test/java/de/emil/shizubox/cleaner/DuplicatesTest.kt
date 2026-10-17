package de.emil.shizubox.cleaner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DuplicatesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(name: String, bytes: ByteArray): FsEntry {
        val f = File(tmp.root, name).apply { writeBytes(bytes) }
        return FsEntry(f.path, f.length(), f.lastModified(), false)
    }

    private fun bytes(size: Int, seed: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    /** Daniel's case: 110 KB, same first 64 KB, different end → not duplicates. */
    @Test fun sameStartDifferentEnd_110k_notDuplicates() = runBlocking {
        val a = bytes(110 * 1024, 1)
        val b = a.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte(); it[80_000] = 7 }
        val ea = file("a.bin", a)
        val eb = file("b.bin", b)
        // The quick hash alone cannot tell them apart – that was the bug.
        assertEquals(Duplicates.quickHash(ea), Duplicates.quickHash(eb))
        assertNotEquals(Duplicates.fullHash(ea), Duplicates.fullHash(eb))
        assertTrue(Duplicates.identicalGroups(listOf(ea, eb)).isEmpty())
    }

    @Test fun identical110k_areDuplicates() = runBlocking {
        val a = bytes(110 * 1024, 3)
        val groups = Duplicates.identicalGroups(listOf(file("a.bin", a), file("b.bin", a.copyOf()), file("c.bin", bytes(110 * 1024, 4))))
        assertEquals(1, groups.size)
        assertEquals(setOf("a.bin", "b.bin"), groups.single().map { File(it.path).name }.toSet())
    }

    @Test fun middleDiffers_bigFile_notDuplicates() = runBlocking {
        // Larger than 128 KB: first and last 64 KB equal, only the middle differs.
        val a = bytes(300 * 1024, 5)
        val b = a.copyOf().also { it[150_000] = (it[150_000] + 1).toByte() }
        assertTrue(Duplicates.identicalGroups(listOf(file("a.bin", a), file("b.bin", b))).isEmpty())
    }

    @Test fun smallFiles_stillCompared() = runBlocking {
        val a = bytes(40 * 1024, 9)
        assertEquals(1, Duplicates.identicalGroups(listOf(file("a.bin", a), file("b.bin", a.copyOf()))).size)
    }
}
