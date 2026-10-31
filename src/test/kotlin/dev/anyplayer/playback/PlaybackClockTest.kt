package dev.anyplayer.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackClockTest {
    @Test fun `clock advances between native events and freezes on pause`() {
        var now = 0L
        val clock = PlaybackClock { now }
        clock.sync(3.0)
        clock.setRunning(true)
        now = 16_000_000
        assertEquals(3.016, clock.position(), .0001)
        now = 32_000_000
        assertEquals(3.032, clock.position(), .0001)
        clock.setRunning(false)
        now = 900_000_000
        assertEquals(3.032, clock.position(), .0001)
    }

    @Test fun `small native corrections never move the running cursor backwards`() {
        var now = 0L
        val clock = PlaybackClock { now }
        clock.setRunning(true)
        now = 250_000_000
        val before = clock.position()
        clock.sync(.20)
        assertEquals(before, clock.position(), .0001)
        now += 16_000_000
        assertTrue(clock.position() > before)
        assertTrue(clock.position() < before + .016)
        clock.setSpeed(2.0)
        val atSpeedChange = clock.position()
        now += 16_000_000
        assertEquals(atSpeedChange + .032, clock.position(), .0001)
    }

    @Test fun `seek rejects stale time events then resumes from acknowledged source time`() {
        var now = 0L
        val clock = PlaybackClock { now }
        clock.setRunning(true)
        clock.seek(12.0)
        now = 100_000_000
        assertFalse(clock.sync(2.0))
        assertEquals(12.0, clock.position(), .0001)
        assertTrue(clock.sync(12.05))
        now += 16_000_000
        assertEquals(12.066, clock.position(), .0001)
        clock.seek(1.0)
        now += 2_100_000_000L
        assertTrue(clock.sync(1.5))
        clock.reset()
        assertEquals(0.0, clock.position(), 0.0)
    }

    @Test fun `missing native events cannot extrapolate without a bound`() {
        var now = 0L
        val clock = PlaybackClock { now }
        clock.setRunning(true)
        now = 30_000_000_000
        assertEquals(1.0, clock.position(), .0001)
    }
}
