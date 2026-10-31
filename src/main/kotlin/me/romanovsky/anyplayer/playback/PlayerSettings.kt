package me.romanovsky.anyplayer.playback

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "AnyPlayerSettings", storages = [Storage("AnyPlayer.xml")])
class PlayerSettings : PersistentStateComponent<PlayerSettings.Options> {
    data class Options(
        var volume: Int = 75,
        var muted: Boolean = false,
        var speed: Float = 1f,
        var animationRepeat: Int = 0,
        var showAnalysis: Boolean = false,
        var analysisRange: String = "Live",
        var audioView: String = "Spectrogram",
        var splitChannels: Boolean = false,
        var hideIgnoredNotice: Boolean = false,
        var followWaveform: Boolean = false,
    )

    private var options = Options()
    override fun getState(): Options = options
    override fun loadState(state: Options) {
        options = state.apply {
            volume = volume.coerceIn(0, 100)
            if (speed !in setOf(.5f, 1f, 1.25f, 1.5f, 2f)) speed = 1f
            animationRepeat = animationRepeat.coerceIn(0, 2)
            if (analysisRange !in setOf("Live", "File")) analysisRange = "Live"
            if (audioView !in setOf("Spectrogram", "Waveform")) audioView = "Spectrogram"
        }
    }
}
