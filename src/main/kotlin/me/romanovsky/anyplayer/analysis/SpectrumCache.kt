package me.romanovsky.anyplayer.analysis

import java.awt.image.BufferedImage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO
import me.romanovsky.anyplayer.media.MediaSource

class SpectrumCache(private val directory: Path) {
    fun key(source: MediaSource, stream: Int, variant: String = "1600x512-linear-db96-movie-v2"): Path? {
        val path = source.localPath ?: return null
        val identity = "$path|${Files.size(path)}|${Files.getLastModifiedTime(path)}|$stream|$variant"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return directory.resolve("$hash.png")
    }

    fun read(path: Path?): BufferedImage? = path?.takeIf(Files::isRegularFile)?.let { runCatching { ImageIO.read(it.toFile()) }.getOrNull() }

    fun write(path: Path?, image: BufferedImage) {
        if (path == null) return
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "spectrum-", ".tmp")
        try {
            check(ImageIO.write(image, "png", temporary.toFile()))
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
        val files = Files.list(directory).use { entries -> entries.filter { it.fileName.toString().matches(Regex("[a-f0-9]{64}\\.png")) }.toList() }
        var total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0) }
        var count = files.size
        for (candidate in files.sortedBy { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0) }) {
            if (total <= 128L * 1024 * 1024 && count <= 64) break
            runCatching { val size = Files.size(candidate); if (Files.deleteIfExists(candidate)) { total -= size; count-- } }
        }
    }
}
