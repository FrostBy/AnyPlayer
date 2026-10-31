package dev.anyplayer.media

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.Raster
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class ToolProcess(executable: Path, arguments: List<String>, private val source: MediaSource? = null, onErrorLine: (String) -> Unit = {}) : Closeable {
    val process: Process = ProcessBuilder(listOf(executable.toString()) + arguments).start()
    private val diagnostic = StringBuilder()
    private val errors = Thread.ofVirtual().name("AnyPlayer-tool-stderr").start {
        try {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(diagnostic) {
                        diagnostic.append(line).append('\n')
                        if (diagnostic.length > 65_536) diagnostic.delete(0, diagnostic.length - 65_536)
                    }
                    onErrorLine(line)
                }
            }
        } catch (_: IOException) { /* Closing the owned process closes this pipe. */ }
    }

    init { process.outputStream.close() }

    fun diagnostics(): String = synchronized(diagnostic) { source?.redact(diagnostic.toString()) ?: diagnostic.toString() }

    fun readFrame(width: Int, height: Int, alpha: Boolean = false): BufferedImage? {
        require(width in 1..4096 && height in 1..4096) { "Decoded frame dimensions exceed the preview limit." }
        val channels = if (alpha) 4 else 3
        val bytes = process.inputStream.readNBytes(Math.multiplyExact(Math.multiplyExact(width, height), channels))
        if (bytes.isEmpty()) return null
        if (bytes.size != width * height * channels) throw IOException("The media tool returned an incomplete frame. ${diagnostics()}")
        val raster = Raster.createInterleavedRaster(DataBufferByte(bytes, bytes.size), width, height, width * channels, channels, IntArray(channels) { it }, null)
        val colors = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), alpha, false, if (alpha) Transparency.TRANSLUCENT else Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
        return BufferedImage(colors, raster, false, null)
    }

    override fun close() {
        if (process.isAlive) process.destroyForcibly()
        process.inputStream.close()
        process.errorStream.close()
        errors.interrupt()
    }

    fun <T> readOutput(timeoutSeconds: Long, read: (InputStream) -> T): T {
        val output = FutureTask { try { read(process.inputStream) } catch (error: Throwable) { process.destroyForcibly(); throw error } }
        val reader = Thread.ofVirtual().name("AnyPlayer-tool-stdout").start(output)
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw IOException("The media tool timed out after $timeoutSeconds seconds. ${diagnostics().takeLast(2000)}")
            val result = output.get(5, TimeUnit.SECONDS)
            errors.join(1000)
            if (process.exitValue() != 0) throw IOException(diagnostics().takeLast(4000).ifBlank { "The media tool exited with code ${process.exitValue()}." })
            return result
        } finally {
            if (process.isAlive) process.destroyForcibly()
            output.cancel(true)
            reader.interrupt()
        }
    }

    companion object {
        fun capture(executable: Path, arguments: List<String>, source: MediaSource? = null, maxBytes: Int = 4 * 1024 * 1024, timeoutSeconds: Long = 30): ByteArray {
            ToolProcess(executable, arguments, source).use { owned ->
                return owned.readOutput(timeoutSeconds) { input ->
                    val bytes = input.readNBytes(maxBytes + 1)
                    if (bytes.size > maxBytes) {
                        owned.process.destroyForcibly()
                        throw IOException("Media tool output exceeds the permitted size.")
                    }
                    bytes
                }
            }
        }
    }
}
