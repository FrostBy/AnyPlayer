package dev.anyplayer.images

import dev.anyplayer.media.MediaSource
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

object AnimationLoops {
    /** Zero means infinite; positive counts include the initial cycle; null means unavailable. */
    fun read(source: MediaSource): Int? = try {
        val input = source.localPath?.let(Files::newInputStream) ?: source.uri.toURL().openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }.getInputStream()
        input.use { stream ->
            val header = stream.readNBytes(12)
            when {
                header.size >= 6 && String(header, 0, 3, Charsets.US_ASCII) == "GIF" -> {
                    val reader = ImageIO.getImageReadersByFormatName("gif").next()
                    ImageIO.createImageInputStream(java.io.SequenceInputStream(ByteArrayInputStream(header), stream)).use { images ->
                        try {
                            reader.input = images
                            val metadata = reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                            val extensions = metadata.getElementsByTagName("ApplicationExtension")
                            var loops = 1
                            for (index in 0 until extensions.length) {
                                val node = extensions.item(index) as IIOMetadataNode
                                if (node.getAttribute("applicationID") == "NETSCAPE") {
                                    val bytes = node.userObject as? ByteArray
                                    if (bytes != null && bytes.size >= 3 && bytes[0].toInt() == 1) {
                                        val repeats = (bytes[1].toInt() and 255) or ((bytes[2].toInt() and 255) shl 8)
                                        // Netscape GIF stores repeats after the first cycle; APNG/WebP store total plays.
                                        loops = if (repeats == 0) 0 else repeats + 1
                                    }
                                }
                            }
                            loops
                        } finally { reader.dispose() }
                    }
                }
                header.size == 12 && header[0] == 137.toByte() && String(header, 1, 3, Charsets.US_ASCII) == "PNG" -> {
                    val data = DataInputStream(java.io.SequenceInputStream(ByteArrayInputStream(header.copyOfRange(8, 12)), stream))
                    var read = 0L
                    var loops = 1
                    while (read < 1024 * 1024) {
                        val length = data.readInt().toLong() and 0xffffffffL
                        val type = String(data.readNBytes(4), Charsets.US_ASCII)
                        if (type == "acTL") { data.readInt(); loops = data.readInt().also { require(it >= 0) }; break }
                        if (type == "IDAT" || type == "IEND") break
                        require(length <= 1024 * 1024) { "Animation metadata exceeds the inspection limit." }
                        data.skipNBytes(length + 4)
                        read += length + 12
                    }
                    loops
                }
                header.size == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> {
                    val data = DataInputStream(stream)
                    var read = 0L
                    var loops = 1
                    while (read < 1024 * 1024) {
                        val type = String(data.readNBytes(4), Charsets.US_ASCII)
                        if (type.length != 4) break
                        val length = Integer.reverseBytes(data.readInt()).toLong() and 0xffffffffL
                        if (type == "ANIM") { data.skipNBytes(4); loops = (data.readUnsignedByte() or (data.readUnsignedByte() shl 8)); break }
                        if (type == "ANMF" || type == "VP8 " || type == "VP8L") break
                        require(length <= 1024 * 1024) { "Animation metadata exceeds the inspection limit." }
                        data.skipNBytes(length + length % 2)
                        read += length + 8
                    }
                    loops
                }
                else -> 1
            }
        }
    } catch (_: Exception) { null }
}
