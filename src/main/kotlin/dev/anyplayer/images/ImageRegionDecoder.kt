package dev.anyplayer.images

import dev.anyplayer.media.MediaSource
import dev.anyplayer.media.ToolProcess
import java.awt.Rectangle
import java.nio.file.Path
import javax.imageio.ImageIO

object ImageRegionDecoder {
    fun read(ffmpeg: Path, source: MediaSource, bounds: Rectangle, zoom: Double): ImageRegion {
        require(bounds.x >= 0 && bounds.y >= 0 && bounds.width > 0 && bounds.height > 0 && zoom.isFinite() && zoom > 0)
        val scale = minOf(1.0, zoom)
        val width = kotlin.math.ceil(bounds.width * scale).toInt().coerceAtLeast(1)
        val height = kotlin.math.ceil(bounds.height * scale).toInt().coerceAtLeast(1)
        val pixels = width.toLong() * height
        require(pixels <= 16_777_216) { "The inspection viewport exceeds the image memory limit. Narrow the viewer or zoom in." }
        val filter = "format=rgba,crop=${bounds.width}:${bounds.height}:${bounds.x}:${bounds.y}:exact=1,scale=$width:$height"
        val bytes = ToolProcess.capture(ffmpeg, listOf("-v", "error", "-nostdin", "-i", source.argument, "-frames:v", "1", "-vf", filter,
            "-f", "image2pipe", "-c:v", "png", "pipe:1"), source, maxBytes = (pixels * 5 + 1_048_576).toInt())
        return ImageRegion(Rectangle(bounds), checkNotNull(ImageIO.read(bytes.inputStream())) { "Original image pixels could not be decoded." })
    }
}
