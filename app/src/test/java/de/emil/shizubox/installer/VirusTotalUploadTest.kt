package de.emil.shizubox.installer

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress

class VirusTotalUploadTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Daniel's case: a file name with umlauts made the fixed length too short (chars ≠ UTF-8 bytes). */
    @Test fun umlautFileName_uploadsCompleteBody() {
        var received = ByteArray(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/files") { ex ->
                received = ex.requestBody.readBytes()
                val answer = """{"data":{"id":"abc"}}""".toByteArray()
                ex.sendResponseHeaders(200, answer.size.toLong())
                ex.responseBody.use { it.write(answer) }
            }
            start()
        }
        try {
            val content = ByteArray(100_000) { (it % 251).toByte() }
            val f = tmp.newFile("Prüfung äöü ß.apk").apply { writeBytes(content) }
            val id = VirusTotal.upload("http://127.0.0.1:${server.address.port}/files", "key", f) {}
            assertEquals("abc", id)
            val text = String(received, Charsets.UTF_8)
            assertTrue(text.contains("filename=\"Prüfung äöü ß.apk\""))
            // The file itself arrives unchanged.
            val start = received.indexOfSequence("\r\n\r\n".toByteArray()) + 4
            assertArrayEquals(content, received.copyOfRange(start, start + content.size))
        } finally {
            server.stop(0)
        }
    }

    @Test fun quotesAndLineBreaks_cannotBreakTheHeader() {
        val head = String(multipartHead("b", "a\"b\r\nX-Evil: 1.apk"), Charsets.UTF_8)
        assertTrue(head.contains("filename=\"a_b__X-Evil: 1.apk\""))
    }

    private fun ByteArray.indexOfSequence(seq: ByteArray): Int {
        outer@ for (i in 0..size - seq.size) {
            for (j in seq.indices) if (this[i + j] != seq[j]) continue@outer
            return i
        }
        return -1
    }
}
