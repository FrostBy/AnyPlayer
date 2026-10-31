package me.romanovsky.anyplayer.analysis

import java.awt.image.BufferedImage
import java.nio.file.Files
import me.romanovsky.anyplayer.media.MediaSource
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpectrumCacheTest {
    @Rule @JvmField val temporary = TemporaryFolder()

    @Test fun `cache keys change for modified source and selected stream`() {
        val source = temporary.newFile("audio.wav").toPath()
        Files.writeString(source, "first")
        val cache = SpectrumCache(temporary.newFolder("cache").toPath())
        val input = MediaSource.parse(source.toString())
        val first = cache.key(input, 1)
        assertNotEquals(first, cache.key(input, 2))
        assertNotEquals(first, cache.key(input, 1, "waveform-v1"))
        Files.writeString(source, "a different source")
        assertNotEquals(first, cache.key(input, 1))
        assertNull(cache.key(MediaSource.parse("https://media.example.test/source?token=private"), 1))
    }

    @Test fun `generated cache entries remain bounded and remote addresses are not persisted`() {
        val directory = temporary.newFolder("cache").toPath()
        val cache = SpectrumCache(directory)
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        for (index in 0..65) cache.write(directory.resolve("%064x.png".format(index)), image)
        assertEquals(64, Files.list(directory).use { it.count() }.toInt())
        assertNotNull(cache.read(directory.resolve("%064x.png".format(65))))
    }
}
