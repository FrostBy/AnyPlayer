package me.romanovsky.anyplayer.media

import org.junit.Assert.*
import org.junit.Test

class MediaInformationTest {
    @Test fun `properties retain readable units and do not invent missing values`() {
        val result = MediaInformation.tracks("""{"media":{"track":[{"@type":"General","FileSize":"1048576","Duration":"1.5"},{"@type":"Audio","SamplingRate":"48000","BitDepth":"24","Channels":"2"}]}}""")
        assertEquals("1.00 MiB", result[0].properties.toMap()["File size"])
        assertEquals("1.500 s", result[0].properties.toMap()["Duration"])
        assertEquals("48.00 kHz", result[1].properties.toMap()["Sampling rate"])
        assertFalse(result[1].properties.toMap().containsKey("Bit rate"))
    }

    @Test fun `unknown strings survive inspection`() {
        assertEquals("Unknown", MediaInformation.tracks("""{"media":{"track":[{"@type":"Video","FrameRate":"Unknown"}]}}""").single().properties.single().second)
    }
}
