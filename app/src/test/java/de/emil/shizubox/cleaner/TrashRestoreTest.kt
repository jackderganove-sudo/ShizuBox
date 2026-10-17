package de.emil.shizubox.cleaner

import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRestoreTest {
    /** A file system whose rename behaves like the shell service: never overwrites. */
    private class FakeFs(vararg files: Pair<String, String>) {
        val map = linkedMapOf(*files)
        fun rename(from: String, to: String): String? = when {
            to in map -> ShizukuManager.RENAME_EXISTS
            from !in map -> "ENOENT"
            else -> { map[to] = map.remove(from)!!; null }
        }
    }

    private val orig = "/storage/emulated/0/Download/a.jpg"

    /** Daniel's case: two trash items with the same original path → "restore all" → both are there. */
    @Test fun twoItemsSameOriginal_bothRestored() = runBlocking {
        val fs = FakeFs("/trash/1" to "first", "/trash/2" to "second")
        val (t1, _) = restoreInto(orig, false) { fs.rename("/trash/1", it) }
        val (t2, _) = restoreInto(orig, false) { fs.rename("/trash/2", it) }
        assertEquals(orig, t1)
        assertEquals("/storage/emulated/0/Download/a (2).jpg", t2)
        assertEquals("first", fs.map[orig])
        assertEquals("second", fs.map[t2])
        assertEquals(2, fs.map.size)
    }

    @Test fun originalTakenByNewFile_newFileStays() = runBlocking {
        val fs = FakeFs(orig to "new", "/trash/1" to "old")
        val (t, _) = restoreInto(orig, false) { fs.rename("/trash/1", it) }
        assertEquals("new", fs.map[orig])
        assertEquals("old", fs.map[t])
    }

    @Test fun otherError_stopsWithoutTryingFurtherNames() = runBlocking {
        var calls = 0
        val (t, err) = restoreInto(orig, false) { calls++; "EXDEV" }
        assertNull(t)
        assertEquals("EXDEV", err)
        assertEquals(1, calls)
    }

    @Test fun folderNames_keepDots() {
        val c = restoreCandidates("/x/my.folder", dir = true).take(2).toList()
        assertEquals(listOf("/x/my.folder", "/x/my.folder (2)"), c)
    }

    @Test fun failedCheck_keepsIndex() {
        val items = listOf(
            TrashItem(1, "/a", "/trash/1", 1, false, 0, CleanTool.System),
            TrashItem(2, "/b", "/trash/2", 1, false, 0, CleanTool.System),
        )
        // existing() failed → null → nothing is dropped.
        assertTrue(staleRows(items, null).isEmpty())
        // A successful check that finds only one file drops the other row.
        assertEquals(listOf(2L), staleRows(items, setOf("/trash/1")))
    }
}
