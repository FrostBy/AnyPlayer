package me.romanovsky.anyplayer.media

import com.google.gson.JsonParser
import java.util.Locale

data class InformationTrack(val title: String, val properties: List<Pair<String, String>>)

object MediaInformation {
    fun tracks(json: String): List<InformationTrack> {
        val tracks = JsonParser.parseString(json).asJsonObject.getAsJsonObject("media")?.getAsJsonArray("track") ?: return emptyList()
        return tracks.map { element ->
            val track = element.asJsonObject
            val type = track.get("@type")?.asString ?: "Track"
            fun text(key: String): String? = track.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            val keys = listOf(
                "CompleteName" to "File", "Format" to "Format", "Format_Profile" to "Profile", "CodecID" to "Codec",
                "FileSize" to "File size", "Duration" to "Duration", "BitRate" to "Bit rate", "OverallBitRate" to "Overall bit rate",
                "Width" to "Width", "Height" to "Height", "FrameRate" to "Frame rate", "FrameCount" to "Frame count",
                "ColorSpace" to "Color", "ChromaSubsampling" to "Chroma subsampling", "BitDepth" to "Bit depth",
                "Channels" to "Channels", "ChannelLayout" to "Channel layout", "SamplingRate" to "Sampling rate",
                "Language" to "Language", "Title" to "Title", "Default" to "Default", "Forced" to "Forced",
            )
            val values = keys.mapNotNull { (key, label) -> text(key)?.let { label to format(key, it) } }
            InformationTrack(type + (text("StreamOrder")?.let { " / $it" } ?: ""), values)
        }
    }

    private fun format(key: String, value: String): String {
        val number = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return value
        fun decimal(value: Double, suffix: String) = String.format(Locale.ROOT, "%.2f %s", value, suffix)
        return when (key) {
            "FileSize" -> when {
                number >= 1024 * 1024 * 1024 -> decimal(number / (1024 * 1024 * 1024), "GiB")
                number >= 1024 * 1024 -> decimal(number / (1024 * 1024), "MiB")
                number >= 1024 -> decimal(number / 1024, "KiB")
                else -> "${number.toLong()} bytes"
            }
            "Duration" -> String.format(Locale.ROOT, "%.3f s", number)
            "BitRate", "OverallBitRate" -> if (number >= 1_000_000) decimal(number / 1_000_000, "Mb/s") else decimal(number / 1000, "kb/s")
            "SamplingRate" -> decimal(number / 1000, "kHz")
            "FrameRate" -> String.format(Locale.ROOT, "%.3f FPS", number)
            "Width", "Height" -> "${number.toInt()} px"
            "BitDepth" -> "${number.toInt()} bits"
            else -> value
        }
    }
}
