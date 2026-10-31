package me.romanovsky.anyplayer.media

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Path
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

data class VideoFrameWindow(val times: List<Double>, val atEnd: Boolean)

data class SourceStream(val index: Int, val id: Int?, val type: String, val codec: String, val width: Int, val height: Int, val sampleRate: Int, val channels: Int, val title: String, val language: String, val start: Double)
data class ProbeResult(val duration: Double?, val start: Double, val streams: List<SourceStream>, val format: String) {
    // VLC's MP4 demuxer exposes the movie timeline, including leading empty edits.
    val analysisOrigin: Double get() = if (format.split(',').any { it in setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2") }) 0.0 else start
    val audio: List<SourceStream> get() = streams.filter { it.type == "audio" }
    val video: SourceStream? get() = streams.firstOrNull { it.type == "video" }
    val animation: Boolean get() = video?.codec in setOf("gif", "apng", "webp_anim")
    val stillImage: Boolean get() = audio.isEmpty() && video?.codec in setOf("png", "mjpeg", "jpeg2000", "bmp", "webp", "av1") && (format.contains("image", true) || format.endsWith("_pipe") || format.contains("avif"))

    fun audioStream(nativeId: Int, nativeTrackIds: List<Int>): SourceStream? {
        audio.singleOrNull { it.id == nativeId }?.let { return it }
        val ordinal = nativeTrackIds.filter { it >= 0 }.indexOf(nativeId)
        return if (nativeTrackIds.count { it >= 0 } == audio.size && ordinal >= 0) audio.getOrNull(ordinal) else null
    }
}

data class AnimationFrame(val time: Double, val duration: Double)

object MediaProbe {
    fun videoFrames(tool: Path, source: MediaSource, stream: Int, origin: Double, position: Double): VideoFrameWindow {
        require(position.isFinite() && position >= 0)
        val network = if (source.localPath == null) listOf("-rw_timeout", "5000000") else emptyList()
        val arguments = listOf("-v", "error") + network + listOf("-read_intervals", "${(position - 1).coerceAtLeast(0.0) + origin}%",
            "-select_streams", stream.toString(), "-show_frames", "-show_entries", "frame=best_effort_timestamp_time", "-of", "csv=p=0", source.argument)
        ToolProcess(tool, arguments, source).use { owned ->
            val read = FutureTask<VideoFrameWindow> {
                val times = ArrayList<Double>()
                var futureFrames = 0
                var atEnd = true
                owned.process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val time = line.substringBefore(',').toDoubleOrNull()?.minus(origin) ?: continue
                        if (!time.isFinite() || time < 0) continue
                        check(times.size < 10_000) { "The video frame index exceeds the inspection limit." }
                        times.add(time)
                        if (time > position + .0005 && ++futureFrames >= 64) { atEnd = false; break }
                    }
                }
                check(times.isNotEmpty()) { "No video frame timestamps are available. ${owned.diagnostics().takeLast(1000)}" }
                VideoFrameWindow(times.distinct().sorted(), atEnd)
            }
            val reader = Thread.ofVirtual().name("AnyPlayer-frame-index").start(read)
            try { return read.get(15, TimeUnit.SECONDS) }
            finally { read.cancel(true); reader.interrupt() }
        }
    }

    private fun JsonObject.text(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.number(name: String): Double = text(name).toDoubleOrNull() ?: 0.0

    fun inspect(tool: Path, source: MediaSource): ProbeResult {
        val network = if (source.localPath == null) listOf("-rw_timeout", "15000000") else emptyList()
        val bytes = ToolProcess.capture(tool, listOf("-v", "error") + network + listOf("-show_format", "-show_streams", "-of", "json", "-i", source.argument), source)
        return parse(bytes.toString(Charsets.UTF_8))
    }

    fun parse(json: String): ProbeResult {
        val root = JsonParser.parseString(json).asJsonObject
        val format = root.getAsJsonObject("format") ?: JsonObject()
        val streams = root.getAsJsonArray("streams")?.map { element ->
            val stream = element.asJsonObject
            val tags = stream.getAsJsonObject("tags") ?: JsonObject()
            val rawId = stream.text("id")
            val id = if (rawId.startsWith("0x")) rawId.drop(2).toIntOrNull(16) else rawId.toIntOrNull()
            SourceStream(stream.number("index").toInt(), id, stream.text("codec_type"), stream.text("codec_name"), stream.number("width").toInt(), stream.number("height").toInt(), stream.number("sample_rate").toInt(), stream.number("channels").toInt(), tags.text("title"), tags.text("language"), stream.number("start_time"))
        }.orEmpty()
        return ProbeResult(format.number("duration").takeIf { it.isFinite() && it > 0 }, format.number("start_time"), streams, format.text("format_name"))
    }

    fun animationFrames(tool: Path, source: MediaSource): List<AnimationFrame> {
        val bytes = ToolProcess.capture(tool, listOf("-v", "error", "-select_streams", "v:0", "-show_frames", "-show_entries", "frame=best_effort_timestamp_time,duration_time,pkt_duration_time", "-of", "json", "-i", source.argument), source, maxBytes = 16 * 1024 * 1024, timeoutSeconds = 60)
        val frames = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject.getAsJsonArray("frames") ?: error("No animation frames were found.")
        val timestamps = frames.map { it.asJsonObject.number("best_effort_timestamp_time") }
        return frames.mapIndexed { index, value ->
            val frame = value.asJsonObject
            val duration = (timestamps.getOrNull(index + 1)?.minus(timestamps[index]) ?: frame.number("duration_time").takeIf { it > 0 } ?: frame.number("pkt_duration_time")).coerceAtLeast(.01)
            AnimationFrame(timestamps[index] - timestamps.first(), duration)
        }.also { require(it.isNotEmpty()) { "No animation frames were found." } }
    }
}
