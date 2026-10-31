package me.romanovsky.anyplayer.media

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

object MediaThumbnail {
    fun read(ffmpeg: Path, source: MediaSource, stream: Int, origin: Double, time: Double, width: Int = 320, height: Int = 180, accurate: Boolean = true): BufferedImage {
        require(time.isFinite() && time >= 0)
        require(width in 1..3840 && height in 1..2160)
        val network = if (source.localPath == null) listOf("-rw_timeout", "5000000") else emptyList()
        val seeking = if (accurate) emptyList() else listOf("-noaccurate_seek")
        val bytes = ToolProcess.capture(ffmpeg, listOf("-v", "error", "-nostdin", "-threads", "2") + seeking + listOf("-seek_timestamp", "1", "-ss", (time + origin).toString()) +
            network + listOf("-i", source.argument, "-map", "0:$stream", "-an", "-sn", "-frames:v", "1", "-vf",
            "scale=$width:$height:force_original_aspect_ratio=decrease", "-f", "image2pipe", "-c:v", "png", "pipe:1"),
            source, maxBytes = width * height * 4 + 1024 * 1024, timeoutSeconds = 10)
        return checkNotNull(ImageIO.read(bytes.inputStream())) { "No preview frame is available at this time." }
    }
}
