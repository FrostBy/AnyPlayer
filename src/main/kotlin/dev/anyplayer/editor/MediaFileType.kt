package dev.anyplayer.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object MediaFileType : FileType {
    override fun getName(): String = "AnyPlayer Media"
    override fun getDescription(): String = "Video and audio media"
    override fun getDefaultExtension(): String = "mp4"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/media.svg", MediaFileType::class.java)
    override fun isBinary(): Boolean = true
}
