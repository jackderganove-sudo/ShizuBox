package de.emil.shizubox.cleaner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCleanScopeTest {
    /** Daniel's case: auto clean took every .log and .tmp file, also in Documents/ and Download/. */
    @Test fun userFolders_neverInAutoClean() {
        assertFalse(autoCleanAllowed(SysCat.LogsTemp, "Documents/Tagebuch.log"))
        assertFalse(autoCleanAllowed(SysCat.LogsTemp, "Download/export.tmp"))
        assertFalse(autoCleanAllowed(SysCat.LogsTemp, "download/sub/x.log"))
        assertFalse(autoCleanAllowed(SysCat.LogsTemp, "DCIM/.tmp"))
        assertFalse(autoCleanAllowed(SysCat.LogsTemp, "notes.log"))
    }

    @Test fun junkPlaces_stillCleaned() {
        assertTrue(autoCleanAllowed(SysCat.LogsTemp, "tencent/logs/a.log"))
        assertTrue(autoCleanAllowed(SysCat.LogsTemp, ".tmp/x"))
        assertTrue(autoCleanAllowed(SysCat.LogsTemp, "SomeApp/.temp"))
    }

    @Test fun otherCategories_unchanged() {
        assertTrue(autoCleanAllowed(SysCat.Thumbnails, "DCIM/.thumbnails"))
        assertTrue(autoCleanAllowed(SysCat.Partial, "Download/a.crdownload"))
    }
}
