package dev.anyplayer.playback

import com.intellij.openapi.components.Service
import uk.co.caprica.vlcj.player.base.MediaPlayer

@Service(Service.Level.PROJECT)
class PlaybackCoordinator {
    private var owner: MediaPlayer? = null

    @Synchronized
    fun play(player: MediaPlayer) {
        owner?.takeIf { it !== player }?.controls()?.setPause(true)
        owner = player
        player.controls().play()
    }

    @Synchronized
    fun forget(player: MediaPlayer) {
        if (owner === player) owner = null
    }
}
