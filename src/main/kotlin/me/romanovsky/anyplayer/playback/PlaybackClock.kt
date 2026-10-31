package me.romanovsky.anyplayer.playback

import kotlin.math.abs

class PlaybackClock(private val nanos: () -> Long = System::nanoTime) {
    private var anchor = 0.0
    private var updated = nanos()
    private var speed = 1.0
    private var correction = 0.0
    private var running = false
    private var seeking = false

    @Synchronized fun position(): Double = positionAt(nanos())

    private fun positionAt(now: Long): Double = anchor + if (running && !seeking) {
        ((now - updated).coerceAtLeast(0) / 1_000_000_000.0).coerceAtMost(1.0) * (speed + correction)
    } else 0.0

    @Synchronized fun sync(seconds: Double): Boolean {
        if (!seconds.isFinite() || seconds < 0) return false
        val now = nanos()
        val expected = anchor + if (running) (now - updated).coerceAtLeast(0) / 1_000_000_000.0 * speed else 0.0
        if (seeking && abs(seconds - expected) > .2 && now - updated < 2_000_000_000L) return false
        val estimate = positionAt(now)
        val error = seconds - estimate
        anchor = if (!running || seeking || abs(error) > .2) seconds else estimate
        correction = if (!running || seeking || abs(error) > .2) 0.0 else (error * 2).coerceIn(-speed / 4, speed / 4)
        seeking = false
        updated = now
        return true
    }

    @Synchronized fun setRunning(value: Boolean) {
        anchor = positionAt(nanos())
        updated = nanos()
        running = value
    }

    @Synchronized fun setSpeed(value: Double) {
        require(value.isFinite() && value > 0)
        anchor = positionAt(nanos())
        updated = nanos()
        speed = value
        correction = 0.0
    }

    @Synchronized fun seek(seconds: Double) {
        require(seconds.isFinite() && seconds >= 0)
        anchor = seconds
        updated = nanos()
        correction = 0.0
        seeking = true
    }

    @Synchronized fun reset() {
        anchor = 0.0
        updated = nanos()
        correction = 0.0
        running = false
        seeking = false
    }
}
