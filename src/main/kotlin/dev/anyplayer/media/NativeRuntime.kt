package dev.anyplayer.media

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.sun.jna.NativeLibrary
import java.nio.file.Files
import java.nio.file.Path

object NativeRuntime {
    private val root: Path get() = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("dev.anyplayer"))) { "AnyPlayer plugin location is unavailable." }.pluginPath.resolve("native")
    private val windows: Boolean get() = System.getProperty("os.name").startsWith("Windows", true)

    fun tool(name: String): Path = root.resolve("bin").resolve(name + if (windows) ".exe" else "").also {
        check(Files.isRegularFile(it) && Files.isExecutable(it)) { "The packaged $name tool is unavailable. Reinstall AnyPlayer for this IDE platform." }
    }

    @Synchronized
    fun loadVlc() {
        val directory = root.resolve("vlc").let { if (windows) it else it.resolve("lib") }
        val library = directory.resolve(when {
            windows -> "libvlc.dll"
            System.getProperty("os.name").startsWith("Mac", true) -> "libvlc.dylib"
            else -> "libvlc.so"
        })
        check(Files.isRegularFile(library)) { "The packaged media engine is unavailable. Reinstall AnyPlayer for this IDE platform." }
        NativeLibrary.addSearchPath("libvlc", directory.toString())
        NativeLibrary.addSearchPath("libvlccore", directory.toString())
    }
}
