package de.emil.shizubox.cleaner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardExclusionTest {
    private val wa = setOf("com.whatsapp")

    /** Daniel's case: WhatsApp excluded, but /WhatsApp/Media/... was not protected. */
    @Test fun knownFolderOfExcludedApp_isProtected() {
        assertTrue(excludedByApp("WhatsApp/Media/WhatsApp Images/IMG-1.jpg", wa))
        assertTrue(excludedByApp("WhatsApp", wa))
        assertTrue(excludedByApp("whatsapp/Databases/msgstore.db", wa))
    }

    @Test fun folderNamedLikeThePackage_isProtected() {
        assertTrue(excludedByApp("com.whatsapp/x.log", wa))
    }

    @Test fun otherFolders_notAffected() {
        assertFalse(excludedByApp("Telegram/a.tmp", wa))
        assertFalse(excludedByApp("Download/WhatsApp/a.tmp", wa))
        assertFalse(excludedByApp("WhatsApp/a.tmp", emptySet()))
        // Android/ is handled by the per-package rules in blockReason.
        assertFalse(excludedByApp("Android/media/com.whatsapp/x", wa))
    }

    @Test fun businessVariant_alsoProtectsTheFolder() {
        assertTrue(excludedByApp("WhatsApp/Media/a.jpg", setOf("com.whatsapp.w4b")))
    }
}
