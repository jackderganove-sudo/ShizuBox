package de.emil.shizubox.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlTest {
    @Test fun bracketAroundLink_isStripped() {
        assertEquals("https://youtu.be/abc", sharedUrl("Schau mal (https://youtu.be/abc)"))
    }

    @Test fun sentencePunctuation_isStripped() {
        assertEquals("https://example.com/a", sharedUrl("Hier: https://example.com/a."))
        assertEquals("https://example.com/a", sharedUrl("„https://example.com/a“, oder?"))
    }

    @Test fun bracketOfTheLinkItself_stays() {
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", sharedUrl("https://en.wikipedia.org/wiki/Foo_(bar)"))
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", sharedUrl("(https://en.wikipedia.org/wiki/Foo_(bar))"))
    }

    @Test fun noLink() {
        assertNull(sharedUrl("kein Link hier"))
    }
}
