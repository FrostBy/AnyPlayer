package dev.anyplayer.analysis

import dev.anyplayer.media.MediaSource
import dev.anyplayer.media.SourceStream
import dev.anyplayer.media.ToolProcess
import java.awt.image.BufferedImage
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

data class TimedImage(val time: Double, val image: BufferedImage)

class SpectrumSession(
    ffmpeg: Path,
    source: MediaSource,
    stream: SourceStream,
    sourceStart: Double,
    position: Double,
    canSeek: Boolean = true,
    private val failed: (String) -> Unit,
) : Closeable {
    val width = 640
    val height = 256
    val windowSeconds = width * 512.0 / stream.sampleRate.coerceAtLeast(1)
    val lookAheadSeconds = if (canSeek) windowSeconds / 2 else 0.0
    private val closed = AtomicBoolean()
    private val timestamps = ArrayBlockingQueue<Double>(64)
    private val frames = ArrayBlockingQueue<TimedImage>(32)
    private val pts = Regex("\\bpts_time:([-+0-9.eE]+)")
    @Volatile private var process: ToolProcess? = null
    private var displayed: TimedImage? = null
    private val worker = Thread.ofVirtual().name("AnyPlayer-live-spectrum").start {
        try {
            val beginning = (position - windowSeconds / 2).coerceAtLeast(0.0)
            val seek = if (canSeek) listOf("-seek_timestamp", "1", "-ss", (beginning + sourceStart).toString()) else emptyList()
            val padding = if (canSeek) ",aresample=async=1:first_pts=${(beginning * stream.sampleRate).toLong()}" else ""
            val tail = if (canSeek) ",apad=pad_dur=$lookAheadSeconds" else ""
            val arguments = listOf("-hide_banner", "-nostdin", "-loglevel", "info") + seek + listOf(
                "-copyts", "-i", source.argument,
                "-filter_complex", "[0:${stream.index}]asetpts=PTS-($sourceStart)/TB$padding$tail,showspectrum=s=${width}x$height:slide=scroll:mode=combined:color=intensity:scale=log:fscale=lin:overlap=0:fps=15:legend=0:drange=96:limit=0,showinfo[v]",
                "-map", "[v]", "-an", "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1",
            )
            ToolProcess(ffmpeg, arguments, source) { line ->
                pts.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { value ->
                    if (!closed.get()) timestamps.offer(value, 5, TimeUnit.SECONDS)
                }
            }.use { owned ->
                process = owned
                if (closed.get()) return@use
                while (!closed.get()) {
                    val frame = owned.readFrame(width, height) ?: break
                    val timestamp = timestamps.poll(5, TimeUnit.SECONDS) ?: error("The analyzer did not return frame timestamps.")
                    val center = timestamp - lookAheadSeconds
                    if (center >= position - .2) frames.put(TimedImage(center, frame))
                }
                if (!closed.get() && owned.process.waitFor() != 0) error(owned.diagnostics().takeLast(2000))
            }
        } catch (error: Exception) {
            if (!closed.get()) failed(error.message ?: "Audio analysis failed.")
        }
    }

    fun frameAt(position: Double): TimedImage? {
        while (frames.peek()?.time?.let { it <= position + .1 } == true) displayed = frames.poll()
        return displayed?.takeIf { kotlin.math.abs(position - it.time) <= .2 }
    }

    override fun close() {
        closed.set(true)
        process?.close()
        worker.interrupt()
        frames.clear()
        timestamps.clear()
        displayed = null
    }

    companion object {
        fun overview(ffmpeg: Path, source: MediaSource, stream: SourceStream, clockOrigin: Double, duration: Double): BufferedImage {
            require(duration.isFinite() && duration > 0)
            val filter = "[0:${stream.index}]asetpts=PTS-($clockOrigin)/TB,aresample=async=1:first_pts=0,apad=whole_dur=$duration,atrim=duration=$duration,showspectrumpic=s=1600x512:legend=0:mode=combined:color=intensity:scale=log:fscale=lin:drange=96:limit=0[v]"
            val png = ToolProcess.capture(ffmpeg, listOf("-v", "error", "-nostdin", "-copyts", "-i", source.argument, "-filter_complex", filter, "-map", "[v]", "-frames:v", "1", "-f", "image2pipe", "-c:v", "png", "pipe:1"), source, maxBytes = 16 * 1024 * 1024, timeoutSeconds = 180)
            return checkNotNull(ImageIO.read(png.inputStream())) { "The analyzer returned no image." }
        }
    }
}
