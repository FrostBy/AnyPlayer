package me.romanovsky.anyplayer.media

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.sun.jna.NativeLibrary
import java.nio.file.Files
import java.nio.file.Path

object NativeRuntime {
    private val root: Path get() = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("me.romanovsky.anyplayer"))) { "Any Player plugin location is unavailable." }.pluginPath.resolve("native")
    private val windows: Boolean get() = System.getProperty("os.name").startsWith("Windows", true)

    fun tool(name: String): Path = root.resolve("bin").resolve(name + if (windows) ".exe" else "").also {
        check(Files.isRegularFile(it) && Files.isExecutable(it)) { "The packaged $name tool is unavailable. Reinstall Any Player for this IDE platform." }
    }

    @Synchronized
    fun loadVlc(directory: Path = root.resolve("vlc").let { if (windows) it else it.resolve("lib") }) {
        val library = directory.resolve(when {
            windows -> "libvlc.dll"
            System.getProperty("os.name").startsWith("Mac", true) -> "libvlc.dylib"
            else -> "libvlc.so"
        })
        check(Files.isRegularFile(library)) { "The packaged media engine is unavailable. Reinstall Any Player for this IDE platform." }
        NativeLibrary.addSearchPath(if (windows) "libvlc" else "vlc", directory.toString())
        NativeLibrary.addSearchPath(if (windows) "libvlccore" else "vlccore", directory.toString())
    }
}
