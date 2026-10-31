package me.romanovsky.anyplayer.media

import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

class PreviewStoryboard(
    private val ffmpeg: Path,
    private val source: MediaSource,
    private val stream: Int,
    private val origin: Double,
    val duration: Double,
    private val cacheRoot: Path,
) : Closeable {
    val interval = if (source.localPath == null) 1.0 else maxOf(.5, duration / 160)
    private val count = kotlin.math.ceil(duration / interval).toInt().coerceIn(1, if (source.localPath == null) Int.MAX_VALUE else 160)
    private val frames = ConcurrentHashMap<Int, BufferedImage>()
    private val closed = AtomicBoolean()
    private val running = AtomicBoolean()
    @Volatile private var wanted = 0
    private var generated = -1
    private var job: Future<*>? = null
    @Volatile private var owned: ToolProcess? = null
    @Volatile var failure: String? = null
        private set
    val readyCount: Int get() = frames.size
    private val identity = source.localPath?.let { "$it|${Files.size(it)}|${Files.getLastModifiedTime(it)}|$stream|$origin|$duration|208x117-v1" }
    private val directory = identity?.let {
        cacheRoot.resolve(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) })
    } ?: cacheRoot.resolve("session-${java.util.UUID.randomUUID()}")
    private val generationLock = locks.computeIfAbsent(directory) { java.util.concurrent.locks.ReentrantLock() }

    init { require(duration.isFinite() && duration > 0); if (identity != null) start() }

    fun request(time: Double) {
        if (identity != null || closed.get() || peek(time) != null) return
        wanted = ((time / interval).toInt().coerceIn(0, count - 1) / 8) * 8
        start()
    }

    fun peek(time: Double): BufferedImage? = frames[(time / interval).toInt().coerceIn(0, count - 1)]

    fun nearest(time: Double): BufferedImage? {
        val index = (time / interval).toInt().coerceIn(0, count - 1)
        return frames[index] ?: frames.keys.minByOrNull { kotlin.math.abs(it - index) }?.let(frames::get)
    }

    fun start() {
        if (closed.get() || !running.compareAndSet(false, true)) return
        job = queue.submit {
            try {
                generationLock.lockInterruptibly()
                try {
                Files.createDirectories(directory)
                val done = directory.resolve("ready")
                if (identity != null && Files.isRegularFile(done)) {
                    for (index in 0 until count) {
                        val frame = runCatching { ImageIO.read(directory.resolve("%03d.png".format(index)).toFile()) }.getOrNull()
                        if (frame != null) frames[index] = frame
                    }
                    if (frames.size == count) return@submit
                }
                do {
                val first = if (identity != null) 0 else wanted
                val limit = if (identity != null) count else minOf(8, count - first)
                val beginning = first * interval
                val network = if (identity == null) listOf("-rw_timeout", "5000000", "-seek_timestamp", "1", "-ss", (beginning + origin).toString()) else emptyList()
                val filter = "[0:$stream]setpts=PTS-(${origin + beginning})/TB,fps=1/$interval:start_time=0:round=down," +
                    "scale=208:117:force_original_aspect_ratio=decrease,tpad=stop_mode=clone:stop_duration=${limit * interval}[v]"
                val arguments = listOf("-v", "error", "-nostdin", "-y", "-threads", "2") + network + listOf("-copyts", "-i", source.argument,
                    "-filter_complex_threads", "1", "-filter_complex", filter, "-map", "[v]", "-an", "-sn", "-frames:v", count.toString(),
                    "-t", (limit * interval).toString(), "-fps_mode", "passthrough", "-start_number", first.toString(), "-atomic_writing", "1", directory.resolve("%03d.png").toString())
                ToolProcess(ffmpeg, arguments, source).use { process ->
                    owned = process
                    var next = first
                    while (!closed.get()) {
                        while (next < first + limit && Files.isRegularFile(directory.resolve("%03d.png".format(next)))) {
                            val frame = ImageIO.read(directory.resolve("%03d.png".format(next)).toFile()) ?: break
                            frames[next++] = frame
                        }
                        if (process.process.waitFor(25, TimeUnit.MILLISECONDS)) {
                            check(process.process.exitValue() == 0) { process.diagnostics().takeLast(1000) }
                            for (index in next until first + limit) {
                                ImageIO.read(directory.resolve("%03d.png".format(index)).toFile())?.let { frames[index] = it }
                            }
                            check((first until first + limit).all(frames::containsKey)) { "The preview strip is incomplete." }
                            if (identity != null) { Files.writeString(done, "ready"); prune() }
                            break
                        }
                    }
                }
                generated = first
                if (identity == null && frames.size > 160) frames.keys.sortedByDescending { kotlin.math.abs(it - wanted) }
                    .take(frames.size - 160).forEach { frames.remove(it); Files.deleteIfExists(directory.resolve("%03d.png".format(it))) }
                } while (!closed.get() && identity == null && generated != wanted)
                } finally { generationLock.unlock() }
            } catch (error: Exception) { if (!closed.get()) failure = source.redact(error.message ?: "Preview preparation failed") }
            finally { owned = null; running.set(false) }
        }
    }

    private fun prune() {
        val files = Files.walk(cacheRoot, 2).use { paths -> paths.filter { Files.isRegularFile(it) && it.parent.fileName.toString().matches(Regex("[a-f0-9]{64}")) && it.fileName.toString().matches(Regex("\\d{3}\\.png")) }.toList() }
        var bytes = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0) }
        var count = files.size
        for (file in files.filter { it.parent != directory && locks[it.parent]?.isLocked != true }.sortedBy { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0) }) {
            if (bytes <= 256L * 1024 * 1024 && count <= 4096) break
            runCatching {
                val size = Files.size(file)
                if (Files.deleteIfExists(file)) { bytes -= size; count--; Files.deleteIfExists(file.parent.resolve("ready")) }
            }
        }
    }

    override fun close() {
        closed.set(true); job?.cancel(true); owned?.close(); frames.clear()
        if (identity == null && Files.isDirectory(directory)) {
            Files.list(directory).use { paths -> paths.forEach { runCatching { Files.deleteIfExists(it) } } }
            runCatching { Files.deleteIfExists(directory) }
        }
    }

    companion object {
        private val locks = ConcurrentHashMap<Path, java.util.concurrent.locks.ReentrantLock>()
        private val queue = AppExecutorUtil.createBoundedApplicationPoolExecutor("AnyPlayer preview indexing", 2)
    }
}
