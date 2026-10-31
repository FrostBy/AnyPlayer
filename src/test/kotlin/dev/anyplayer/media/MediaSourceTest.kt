package dev.anyplayer.media

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MediaSourceTest {
    @Test fun `TypeScript extension is never automatically claimed as video`() {
        assertFalse(MediaSource.supportedExtensions.contains("ts"))
    }
    @Test fun `local paths preserve spaces unicode and shell characters`() {
        val path = Files.createTempFile("AnyPlayer тест & ", ".mp4")
        try {
            val source = MediaSource.parse(path.toString())
            assertEquals(path.toAbsolutePath(), source.localPath)
            assertEquals(path.fileName.toString(), source.name)
            assertEquals("file", source.uri.scheme)
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun `https keeps signed query in playback but removes it from diagnostics`() {
        val source = MediaSource.parse("https://media.example.test/movie.mp4?token=private-token&quality=high")
        assertTrue(source.argument.contains("private-token"))
        assertFalse(source.description.contains("token"))
        assertFalse(source.redact("Failed ${source.argument}: private-token").contains("private-token"))
    }

    @Test fun `direct stream without a filename has a useful name`() {
        assertEquals("media.example.test", MediaSource.parse("https://media.example.test").name)
    }

    @Test fun `redaction preserves unrelated diagnostic numbers`() {
        val source = MediaSource.parse("https://media.example.test/stream?token=1")
        assertEquals("Failed after 15 seconds: token=<redacted>", source.redact("Failed after 15 seconds: token=1"))
    }

    @Test fun `other uri schemes and embedded credentials are rejected`() {
        for (input in listOf("javascript:alert(1)", "ftp://media.example.test/a.mp4", "https://name:password@media.example.test/a.mp4")) {
            assertThrows(IllegalArgumentException::class.java) { MediaSource.parse(input) }
        }
    }

    @Test fun `file uri and relative path identify the same local file`() {
        val directory = Files.createTempDirectory("AnyPlayer-source-")
        val file = Files.writeString(directory.resolve("a #1.mp3"), "fixture")
        try {
            assertEquals(MediaSource.parse(file.toUri().toASCIIString()), MediaSource.parse(file.fileName.toString(), directory))
        } finally { Files.delete(file); Files.delete(directory) }
    }
}
