package dev.anyplayer.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class WaveformAnalysisTest {
    @Test fun `short low rate ranges and long declared durations keep bounded columns`() {
        val pcm = ByteArray(800 * 2) { if (it % 2 == 1) 64 else 0 }
        val short = WaveformAnalysis.envelope(ByteArrayInputStream(pcm), 800, 1, false)
        assertEquals(800, short.width)
        assertNotEquals(0, short.getRGB(400, 200) and 0xFFFFFF)
        val long = WaveformAnalysis.envelope(ByteArrayInputStream(pcm), 48_000L * 7200, 1, false)
        assertEquals(1600, long.width)
        assertEquals(400, long.height)
    }

    @Test fun `invalid incomplete pcm is rejected and silence remains empty`() {
        assertThrows(IllegalArgumentException::class.java) { WaveformAnalysis.envelope(ByteArrayInputStream(byteArrayOf(1)), 1, 1, false) }
        val silence = WaveformAnalysis.envelope(ByteArrayInputStream(ByteArray(1600)), 800, 1, false)
        assertEquals(0, silence.getRGB(400, 200) and 0xFFFFFF)
    }
}
