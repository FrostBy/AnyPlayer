package dev.anyplayer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import dev.anyplayer.media.MediaSource

class MediaEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = !file.isDirectory &&
        (file.getUserData(SOURCE) != null || file.isInLocalFileSystem &&
            (file.getUserData(EXPLICIT_OPEN) == true || file.extension?.lowercase() in MediaSource.supportedExtensions))
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = MediaEditor(project, file)
    override fun getEditorTypeId(): String = "AnyPlayer"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        val SOURCE: Key<MediaSource> = Key.create("AnyPlayer.source")
        val EXPLICIT_OPEN: Key<Boolean> = Key.create("AnyPlayer.explicitOpen")
        fun source(file: VirtualFile): MediaSource = if (file.isInLocalFileSystem) MediaSource.parse(file.path)
            else checkNotNull(file.getUserData(SOURCE)) { "This virtual media source is unavailable." }
    }
}
