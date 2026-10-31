package dev.anyplayer.images

import dev.anyplayer.analysis.TimedImage
import dev.anyplayer.media.MediaSource
import dev.anyplayer.media.ToolProcess
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class AnimationDecoder(
    ffmpeg: Path,
    source: MediaSource,
    private val timeline: AnimationTimeline,
    width: Int,
    height: Int,
    startFrame: Int,
    private val failed: (String) -> Unit,
) : Closeable {
    private val closed = AtomicBoolean()
    private val frames = ArrayBlockingQueue<TimedImage>(4)
    @Volatile private var process: ToolProcess? = null
    private var displayed: TimedImage? = null
    private val worker = Thread.ofVirtual().name("AnyPlayer-animation").start {
        try {
            val scale = minOf(1.0, 1280.0 / width.coerceAtLeast(1), 1280.0 / height.coerceAtLeast(1))
            val outputWidth = (width * scale).toInt().coerceAtLeast(1)
            val outputHeight = (height * scale).toInt().coerceAtLeast(1)
            val arguments = listOf("-v", "error", "-nostdin", "-i", source.argument, "-vf", "select=gte(n\\,$startFrame),scale=$outputWidth:$outputHeight", "-fps_mode", "passthrough", "-frames:v", (timeline.frames.size - startFrame).toString(), "-an", "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1")
            ToolProcess(ffmpeg, arguments, source).use { owned ->
                process = owned
                if (closed.get()) return@use
                for (index in startFrame..timeline.frames.lastIndex) {
                    if (closed.get()) break
                    val image = owned.readFrame(outputWidth, outputHeight, true) ?: error("Animation ended before its indexed frame count. ${owned.diagnostics()}")
                    frames.put(TimedImage(timeline.frames[index].time, image))
                }
                if (!closed.get() && owned.process.waitFor() != 0) error(owned.diagnostics().takeLast(2000))
            }
        } catch (error: Exception) {
            if (!closed.get()) failed(error.message ?: "Animation decoding failed.")
        }
    }

    fun frameAt(position: Double): TimedImage? {
        while (frames.peek()?.time?.let { it <= position + .00001 } == true) displayed = frames.poll()
        return displayed?.takeIf { timeline.frameAt(it.time) == timeline.frameAt(position) }
    }

    override fun close() {
        closed.set(true)
        process?.close()
        worker.interrupt()
        frames.clear()
        displayed = null
    }
}
