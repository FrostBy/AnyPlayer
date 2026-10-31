package me.romanovsky.anyplayer.analysis

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Path
import me.romanovsky.anyplayer.media.MediaSource
import me.romanovsky.anyplayer.media.SourceStream
import me.romanovsky.anyplayer.media.ToolProcess

object WaveformAnalysis {
    fun render(ffmpeg: Path, source: MediaSource, stream: SourceStream, origin: Double, start: Double, end: Double, split: Boolean, columns: Int = 1600): BufferedImage {
        require(start.isFinite() && end.isFinite() && start >= 0 && end > start)
        require(stream.sampleRate > 0 && stream.channels in 1..64) { "Unsupported audio sample rate or channel count." }
        val firstSample = (start * stream.sampleRate).toLong()
        val filter = "asetpts=PTS-($origin)/TB,aresample=async=1:first_pts=$firstSample," +
            "apad=whole_dur=${end - start},atrim=start=$start:end=$end,asetpts=PTS-STARTPTS"
        val arguments = listOf("-v", "error", "-nostdin", "-seek_timestamp", "1", "-ss", (start + origin).toString(),
            "-copyts", "-i", source.argument, "-filter_complex", "[0:${stream.index}]$filter[out]",
            "-map", "[out]", "-vn", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1")
        val samples = kotlin.math.ceil((end - start) * stream.sampleRate).toLong().coerceAtLeast(1)
        return ToolProcess(ffmpeg, arguments, source).use { owned ->
            owned.readOutput(120) { input -> envelope(input, samples, stream.channels, split, columns) }
        }
    }

    internal fun envelope(input: InputStream, samples: Long, channels: Int, split: Boolean, columns: Int = 1600): BufferedImage {
        require(samples > 0 && channels in 1..64)
        require(columns in 1..33_600)
        val width = minOf(columns.toLong(), samples).toInt()
        val sums = Array(channels) { DoubleArray(width) }
        val counts = LongArray(width)
        val buffer = ByteArray(1024 * channels * 2)
        var position = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val length = input.readNBytes(buffer, 0, buffer.size)
            if (length == 0) break
            require(length % (channels * 2) == 0) { "Incomplete PCM sample." }
            var offset = 0
            while (offset < length) {
                val column = (position.toDouble() / samples * width).toInt().coerceIn(0, width - 1)
                for (channel in 0 until channels) {
                    val value = ((buffer[offset].toInt() and 255) or (buffer[offset + 1].toInt() shl 8)).toShort().toInt()
                    sums[channel][column] += kotlin.math.abs(value)
                    offset += 2
                }
                counts[column]++
                position++
            }
        }
        val image = BufferedImage(width, 400, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            for (channel in 0 until channels) {
                val band = if (split) 400.0 / channels else 400.0
                val center = ((if (split) channel else 0) * band + band / 2).toInt()
                graphics.color = Color(if (channel % 2 == 0) 0x5B8FE8 else 0x26A686)
                for (x in 0 until width) {
                    val mean = if (counts[x] == 0L) 0.0 else sums[channel][x] / counts[x] / 32768
                    val amplitude = (kotlin.math.sqrt(mean) * (band / 2 - 1)).toInt()
                    if (amplitude > 0) graphics.drawLine(x, center - amplitude, x, center + amplitude)
                }
            }
        } finally { graphics.dispose() }
        return image
    }
}
