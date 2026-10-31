package dev.anyplayer.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.anyplayer.media.MediaSource
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

class OpenMediaAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        showOpenMediaDialog(project)
    }

    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = event.project != null }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

fun showOpenMediaDialog(project: Project) {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle("Open media").withDescription("Select a local media file"))
        val dialog = object : DialogWrapper(project) {
            init { title = "Open Media"; setOKButtonText("Open"); init() }
            override fun getPreferredFocusedComponent(): JComponent = field.textField
            override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
                add(JBLabel("Media URL or local file path"), BorderLayout.NORTH)
                add(field, BorderLayout.CENTER)
                preferredSize = JBUI.size(520, 65)
            }
            override fun doValidate(): ValidationInfo? = try {
                MediaSource.parse(field.text, project.basePath?.let(Path::of))
                null
            } catch (error: Exception) { ValidationInfo(error.message ?: "Enter a valid media source.", field) }
        }
        if (!dialog.showAndGet()) return
        val source = MediaSource.parse(field.text, project.basePath?.let(Path::of))
        FileEditorManager.getInstance(project).openFile(mediaFile(source), true)
}

internal fun mediaFile(source: MediaSource): VirtualFile {
    val file = source.localPath?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        ?: LightVirtualFile(source.name, MediaFileType, "").apply { isWritable = false }
    file.putUserData(MediaEditorProvider.SOURCE, source.takeIf { it.localPath == null })
    file.putUserData(MediaEditorProvider.EXPLICIT_OPEN, true)
    return file
}
