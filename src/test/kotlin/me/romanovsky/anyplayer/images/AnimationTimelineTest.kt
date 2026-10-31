package me.romanovsky.anyplayer.images

import me.romanovsky.anyplayer.media.AnimationFrame
import org.junit.Assert.*
import org.junit.Test

class AnimationTimelineTest {
    @Test fun `seeking respects variable delays instead of assumed fps`() {
        val timeline = AnimationTimeline(listOf(AnimationFrame(0.0, .08), AnimationFrame(.08, .24), AnimationFrame(.32, .16)))
        assertEquals(0, timeline.frameAt(-1.0))
        assertEquals(0, timeline.frameAt(.079))
        assertEquals(1, timeline.frameAt(.08))
        assertEquals(1, timeline.frameAt(.31))
        assertEquals(2, timeline.frameAt(.32))
        assertEquals(2, timeline.frameAt(10.0))
        assertEquals(.48, timeline.duration, .00001)
    }

    @Test fun `empty or invalid timelines fail before playback`() {
        assertThrows(IllegalArgumentException::class.java) { AnimationTimeline(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { AnimationTimeline(listOf(AnimationFrame(0.0, -1.0))) }
    }
}
