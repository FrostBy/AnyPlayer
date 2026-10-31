package me.romanovsky.anyplayer.images

import me.romanovsky.anyplayer.media.AnimationFrame

class AnimationTimeline(val frames: List<AnimationFrame>) {
    init {
        require(frames.isNotEmpty() && frames.all { it.time.isFinite() && it.time >= 0 && it.duration.isFinite() && it.duration > 0 })
        require(frames.zipWithNext().all { (previous, next) -> next.time >= previous.time }) { "Animation timestamps must be ordered." }
    }
    val duration: Double get() = frames.last().time + frames.last().duration

    fun frameAt(position: Double): Int {
        val found = frames.binarySearch { it.time.compareTo(position.coerceAtLeast(0.0)) }
        return if (found >= 0) found else (-found - 2).coerceIn(0, frames.lastIndex)
    }
}
