package dev.anyplayer.media

import org.junit.Assert.*
import org.junit.Test

class MediaProbeTest {
    @Test fun `stream ids are distinct from stream indices`() {
        val info = MediaProbe.parse("""{"format":{"duration":"12.5","start_time":"4.0"},"streams":[{"index":3,"id":"0x65","codec_type":"audio","codec_name":"aac"},{"index":7,"id":"0xca","codec_type":"audio","codec_name":"aac"}]}""")
        assertEquals(12.5, info.duration!!, 0.0)
        assertEquals(7, info.audioStream(202, listOf(101, 202))!!.index)
        assertEquals(3, info.audioStream(12, listOf(12, 25))!!.index)
        assertNull(info.audioStream(12, listOf(12)))
    }

    @Test fun `live input does not invent a duration`() {
        assertNull(MediaProbe.parse("""{"format":{"format_name":"hls"},"streams":[]}""").duration)
    }

    @Test fun `content identifies an image even without a filename extension`() {
        val image = MediaProbe.parse("""{"format":{"format_name":"png_pipe"},"streams":[{"index":0,"codec_type":"video","codec_name":"png","width":40,"height":24}]}""")
        assertTrue(image.stillImage)
        val video = MediaProbe.parse("""{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2"},"streams":[{"index":0,"codec_type":"video","codec_name":"av1"}]}""")
        assertFalse(video.stillImage)
    }
}
