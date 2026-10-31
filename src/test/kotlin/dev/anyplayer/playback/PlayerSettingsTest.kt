package dev.anyplayer.playback

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.*
import org.junit.Test

class PlayerSettingsTest {
    @Test fun `preferences survive platform XML serialization`() {
        val original = PlayerSettings.Options(volume = 23, muted = true, speed = 1.5f, animationRepeat = 2,
            showAnalysis = true, analysisRange = "File", audioView = "Waveform", splitChannels = true, hideIgnoredNotice = true, followWaveform = true)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(original), PlayerSettings.Options::class.java)
        val settings = PlayerSettings()
        settings.loadState(restored)
        assertEquals(original, settings.state)
    }

    @Test fun `invalid saved control values fall back to usable defaults`() {
        val settings = PlayerSettings()
        settings.loadState(PlayerSettings.Options(volume = -50, speed = Float.NaN, animationRepeat = 99, audioView = "missing", analysisRange = "missing"))
        assertEquals(0, settings.state.volume)
        assertEquals(1f, settings.state.speed)
        assertEquals(2, settings.state.animationRepeat)
        assertEquals("Spectrogram", settings.state.audioView)
        assertEquals("Live", settings.state.analysisRange)
    }
}
