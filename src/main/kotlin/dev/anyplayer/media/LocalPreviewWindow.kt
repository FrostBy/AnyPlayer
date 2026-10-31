package dev.anyplayer.media

import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class PreviewFrame(val time: Double, val image: BufferedImage, val refined: Boolean)

class LocalPreviewWindow(
    private val ffmpeg: Path,
    private val source: MediaSource,
    private val video: SourceStream,
    private val origin: Double,
    private val duration: Double,
) : Closeable {
    private val frames = ConcurrentSkipListMap<Long, PreviewFrame>()
    private val scale = minOf(208.0 / video.width.coerceAtLeast(1), 117.0 / video.height.coerceAtLeast(1))
    private val width = (video.width * scale).toInt().coerceIn(1, 208)
    private val height = (video.height * scale).toInt().coerceIn(1, 117)
    @Volatile private var closed = false
    @Volatile private var generation = 0L
    @Volatile private var desired = 0.0
    @Volatile private var beginning = -1.0
    @Volatile private var end = -1.0
    @Volatile private var owned: ToolProcess? = null
    private var job: Future<*>? = null
    @Volatile var failure: String? = null
        private set

    fun nearest(time: Double): PreviewFrame? {
        val key = (time * 1000).toLong()
        val before = frames.floorEntry(key)?.value
        val after = frames.ceilingEntry(key)?.value
        return listOfNotNull(before, after).minByOrNull { kotlin.math.abs(it.time - time) }
    }

    fun refined(time: Double): PreviewFrame? = frames.values.filter { it.refined && kotlin.math.abs(it.time - time) <= .051 }
        .minByOrNull { kotlin.math.abs(it.time - time) }

    @Synchronized fun focus(time: Double) {
        if (closed || !time.isFinite()) return
        desired = time.coerceIn(0.0, duration)
        if (desired in beginning..end && (job?.isDone == false || (-2..2).all {
                val neighbor = desired + it * .1
                neighbor !in 0.0..duration || refined(neighbor) != null
            })) return
        generation++
        job?.cancel(true)
        owned?.close()
        val ticket = generation
        beginning = (desired - 2).coerceAtLeast(0.0)
        end = (desired + 2).coerceAtMost(duration)
        failure = null
        job = queue.submit {
            try {
                val first = desired
                if (refined(first) == null) runCatching {
                    val image = MediaThumbnail.read(ffmpeg, source, video.index, origin,
                        first.coerceAtMost((duration - .05).coerceAtLeast(0.0)), width, height, accurate = false)
                    store(PreviewFrame(first, image, false), ticket)
                }
                if (closed || ticket != generation) return@submit
                val center = desired
                val start = kotlin.math.floor((center - 2).coerceAtLeast(0.0) * 10) / 10
                val finish = (center + 2).coerceAtMost(duration)
                beginning = start
                end = finish
                val count = (kotlin.math.ceil((finish - start) * 10).toInt() + 1).coerceIn(1, 42)
                val network = if (source.localPath == null) listOf("-rw_timeout", "5000000") else emptyList()
                val filter = "[0:${video.index}]setpts=PTS-(${origin + start})/TB,fps=10:start_time=0:round=near," +
                    "scale=$width:$height,tpad=stop_mode=clone:stop_duration=${finish - start + .1}[v]"
                val args = listOf("-v", "error", "-nostdin", "-threads", "2", "-noaccurate_seek", "-seek_timestamp", "1", "-ss", (start + origin).toString()) +
                    network + listOf("-copyts", "-i", source.argument, "-filter_complex_threads", "1", "-filter_complex", filter,
                    "-map", "[v]", "-an", "-sn", "-frames:v", count.toString(), "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1")
                ToolProcess(ffmpeg, args, source).use { process ->
                    synchronized(this) {
                        if (closed || generation != ticket) { process.close(); return@submit }
                        owned = process
                    }
                    val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({ process.close() }, 15, TimeUnit.SECONDS)
                    try {
                        var decoded = 0
                        for (index in 0 until count) {
                            if (closed || generation != ticket) break
                            val image = process.readFrame(width, height) ?: break
                            decoded++
                            val position = start + index / 10.0
                            if (position <= duration) store(PreviewFrame(position, image, true), ticket)
                        }
                        check(decoded > 0 || closed || generation != ticket) { "No local preview frames were decoded. ${process.diagnostics().takeLast(1000)}" }
                    } finally { timeout.cancel(false) }
                }
            } catch (error: Exception) {
                if (!closed && generation == ticket) failure = source.redact(error.message ?: "Local preview unavailable")
            } finally { if (generation == ticket) owned = null }
        }
    }

    private fun store(frame: PreviewFrame, ticket: Long) {
        if (closed || generation != ticket) return
        frames[(frame.time * 1000).toLong()] = frame
        if (frames.size > 128) frames.entries.maxByOrNull { kotlin.math.abs(it.value.time - desired) }?.let { frames.remove(it.key) }
    }

    @Synchronized override fun close() {
        closed = true
        generation++
        job?.cancel(true)
        owned?.close()
        frames.clear()
    }

    companion object {
        private val queue = AppExecutorUtil.createBoundedApplicationPoolExecutor("AnyPlayer local preview", 2)
    }
}
