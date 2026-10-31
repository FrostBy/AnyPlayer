package dev.anyplayer.media

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

data class MediaSource(val uri: URI, val localPath: Path? = null) {
    val argument: String get() = localPath?.toString() ?: uri.toASCIIString()
    val name: String get() = localPath?.fileName?.toString()
        ?: uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.host ?: "Media stream"
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val description: String get() = localPath?.toString()
        ?: URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString()

    fun redact(text: String): String {
        var result = text.replace(argument, description).replace(uri.toString(), description)
        uri.rawQuery?.split('&')?.forEach { field ->
            val key = field.substringBefore('=')
            field.substringAfter('=', "").takeIf { it.isNotEmpty() }?.let {
                result = result.replace("$key=$it", "$key=<redacted>")
                if (it.length >= 8) result = result.replace(it, "<redacted>")
            }
        }
        return result
    }

    companion object {
        // Transport-stream .ts is explicit-open only, so TypeScript editors remain unaffected.
        val supportedExtensions = setOf("mp4", "m4v", "mkv", "mov", "webm", "avi", "wmv", "mpeg", "mpg", "m3u8", "mp3", "wav", "flac", "ogg", "opus", "aac", "m4a", "aiff", "png", "jpg", "jpeg", "bmp", "gif", "webp", "apng", "avif")

        fun parse(value: String, baseDirectory: Path? = null, checkExists: Boolean = true): MediaSource {
            val input = value.trim().removeSurrounding("\"")
            require(input.isNotEmpty()) { "Enter a media URL or local file path." }
            val windowsPath = Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(input) || input.startsWith("\\\\")
            if (!windowsPath && Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(input)) {
                val uri = try { URI(input) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid URL. Encode spaces as %20.") }
                if (uri.scheme.equals("file", true)) return local(Path.of(uri), baseDirectory, checkExists)
                require(uri.scheme.lowercase() in setOf("http", "https")) { "Use an HTTP(S) URL or a local file path." }
                require(!uri.host.isNullOrBlank()) { "The URL must contain a host." }
                require(uri.userInfo == null) { "Use a media URL without embedded account credentials." }
                return MediaSource(uri)
            }
            val expanded = if (input == "~" || input.startsWith("~/") || input.startsWith("~\\")) {
                System.getProperty("user.home") + input.drop(1)
            } else input
            return local(Path.of(expanded), baseDirectory, checkExists)
        }

        private fun local(path: Path, baseDirectory: Path?, checkExists: Boolean): MediaSource {
            val absolute = (if (path.isAbsolute) path else (baseDirectory ?: Path.of("")).resolve(path)).toAbsolutePath().normalize()
            if (checkExists) require(Files.isRegularFile(absolute) && Files.isReadable(absolute)) { "Select an existing, readable media file." }
            return MediaSource(absolute.toUri(), absolute)
        }
    }
}
