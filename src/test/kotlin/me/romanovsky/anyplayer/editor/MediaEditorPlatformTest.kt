package me.romanovsky.anyplayer.editor

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import me.romanovsky.anyplayer.analysis.SpectrumView
import me.romanovsky.anyplayer.analysis.WaveformView
import me.romanovsky.anyplayer.images.ImageCanvas
import me.romanovsky.anyplayer.media.LocalPreviewWindow
import me.romanovsky.anyplayer.media.MediaProbe
import me.romanovsky.anyplayer.media.MediaSource
import me.romanovsky.anyplayer.media.NativeRuntime
import me.romanovsky.anyplayer.media.ToolProcess
import me.romanovsky.anyplayer.playback.PlayerSettings

class MediaEditorPlatformTest : BasePlatformTestCase() {
    private val temporary = org.junit.rules.TemporaryFolder.builder().assureDeletion().build()
    private var settingsBefore: PlayerSettings.Options? = null

    override fun setUp() {
        super.setUp()
        temporary.create()
        val settings = service<PlayerSettings>()
        settingsBefore = settings.state.copy()
        settings.loadState(PlayerSettings.Options())
    }

    override fun tearDown() {
        try { settingsBefore?.let { service<PlayerSettings>().loadState(it) } }
        finally {
            try { await("owned temporary fixture cleanup") { try { temporary.delete(); true } catch (_: AssertionError) { false } } }
            finally { super.tearDown() }
        }
    }

    fun testScrubKeepsDisplayResolutionUntilTheLatestSharpFrameArrives() {
        val path = temporary.root.toPath().resolve("hd-scrub.mp4")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=red:s=1280x720:r=12:d=1.5",
            "-f", "lavfi", "-i", "color=blue:s=1280x720:r=12:d=1.5", "-filter_complex", "[0:v][1:v]concat=n=2:v=1:a=0[v]", "-map", "[v]", "-c:v", "libx264", "-preset", "ultrafast", path.toString()))
        val file = LightVirtualFile("hd-scrub.mp4", MediaFileType, "").apply { putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString())) }
        val editor = MediaEditor(project, file)
        try {
            await("HD player ready") { descendants(editor.component).filterIsInstance<JButton>().any { it.accessibleContext.accessibleName == "Play" && it.isEnabled } }
            editor.component.setSize(1200, 900)
            fun layout(component: Component) { if (component is Container) { component.doLayout(); component.components.forEach(::layout) } }
            layout(editor.component)
            val slider = descendants(editor.component).filterIsInstance<MediaSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            slider.value = 100_000
            await("first display-sized still") { descendants(editor.component).filterIsInstance<ImageCanvas>().any { (it.image?.width ?: 0) >= 640 } }
            val canvas = descendants(editor.component).filterIsInstance<ImageCanvas>().single()
            slider.valueIsAdjusting = true
            for (value in listOf(200_000, 400_000, 600_000)) {
                slider.value = value
                val until = System.nanoTime() + 160_000_000L
                while (System.nanoTime() < until) {
                    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                    assertTrue("Hover thumbnails must never replace the main video", canvas.image!!.width >= 640)
                    Thread.sleep(20)
                }
            }
            slider.valueIsAdjusting = false
            val released = System.nanoTime()
            await("latest sharp blue frame") { canvas.image!!.let { it.width >= 640 && Color(it.getRGB(it.width / 2, it.height / 2)).blue > 200 } }
            println("Latest HD scrub still after release: ${(System.nanoTime() - released) / 1_000_000}ms")
        } finally { Disposer.dispose(editor) }
    }

    fun testWaveformFollowSlidesInsideReadyDataWithoutClearingOrRegeneratingIt() {
        val path = temporary.root.toPath().resolve("follow.wav")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=8000:duration=12", "-ac", "2", path.toString()))
        service<PlayerSettings>().state.apply { audioView = "Waveform"; followWaveform = true; muted = true }
        val file = LightVirtualFile("follow.wav", MediaFileType, "").apply { putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString())) }
        val editor = MediaEditor(project, file)
        try {
            val view = descendants(editor.component).filterIsInstance<WaveformView>().single()
            await("full waveform") { view.image != null }
            val zoom = descendants(editor.component).filterIsInstance<JButton>().single { it.text == "Zoom in" }
            zoom.doClick()
            assertNotNull("A valid waveform must stay visible during refinement", view.image)
            zoom.doClick()
            await("buffered waveform neighbors") { view.image != null && view.imageEndMicros > view.viewEndMicros && view.image!!.width >= 3000 }
            val ready = view.image
            descendants(editor.component).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Play" }.doClick()
            await("continuous Follow movement before leaving the viewport") { view.viewStartMicros > 0 }
            var previous = view.viewStartMicros
            val until = System.nanoTime() + 320_000_000L
            var moves = 0
            while (System.nanoTime() < until) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                assertSame("Moving inside prepared data must not regenerate or clear it", ready, view.image)
                val next = view.viewStartMicros
                if (next > previous) moves++
                assertTrue("Follow must not jump by a large range", next - previous in 0..200_000)
                previous = next
                Thread.sleep(20)
            }
            assertTrue("Follow should move between native time events", moves >= 5)
            val slider = descendants(editor.component).filterIsInstance<MediaSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            slider.value = 900_000
            await("distant Follow range without a blank waveform") {
                assertNotNull("The previous valid waveform must remain visible while preparing a distant range", view.image)
                view.viewStartMicros >= 8_500_000 && view.imageEndMicros >= 12_000_000
            }
            val scroll = descendants(editor.component).filterIsInstance<javax.swing.JScrollBar>().single { it.accessibleContext.accessibleName == "Waveform time range" }
            scroll.value = 0
            assertFalse(descendants(editor.component).filterIsInstance<JCheckBox>().single { it.text == "Follow" }.isSelected)
        } finally { Disposer.dispose(editor) }
    }

    fun testExplicitLocalTransportAndExtensionlessMediaKeepTheirVfsIdentity() {
        val path = temporary.root.toPath().resolve("recording.ts")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=blue:s=160x90:d=1", "-an", "-c:v", "libx264", "-f", "mpegts", path.toString()))
        val provider = MediaEditorProvider()
        for (name in listOf("recording.ts", "extensionless")) {
            val input = temporary.root.toPath().resolve(name)
            if (input != path) Files.copy(path, input)
            val initial = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(input)!!
            assertFalse(provider.accept(project, initial))
            val file = mediaFile(MediaSource.parse(input.toString()))
            assertEquals(initial, file)
            assertNull(file.getUserData(MediaEditorProvider.SOURCE))
            assertTrue(provider.accept(project, file))
            assertTrue("Explicit opening must preserve the physical file", Files.isSameFile(input, MediaEditorProvider.source(file).localPath))
            val editor = provider.createEditor(project, file)
            try { await("explicit $name playback") { descendants(editor.component).filterIsInstance<JButton>().any { it.accessibleContext.accessibleName == "Play" && it.isEnabled } } }
            finally { Disposer.dispose(editor) }
        }
        val code = temporary.root.toPath().resolve("source.ts")
        Files.writeString(code, "const value = 1;")
        assertFalse(provider.accept(project, com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(code)!!))
    }

    fun testSilentVideoExplainsUnavailableAudioAndUsesCorrectPointers() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-silent-", ".mp4")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=blue:s=160x90:d=1", "-an", "-c:v", "libx264", "-y", path.toString()))
        val file = LightVirtualFile("silent.mp4", MediaFileType, "").apply { putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString())) }
        val editor = MediaEditor(project, file)
        try {
            await("silent video ready") { descendants(editor.component).filterIsInstance<JButton>().any { it.accessibleContext.accessibleName == "Play" && it.isEnabled } }
            val tabs = descendants(editor.component).filterIsInstance<JTabbedPane>().first { it.getTitleAt(0) == "Preview" }
            assertFalse(tabs.isEnabledAt(1)); assertFalse(tabs.isEnabledAt(2))
            assertEquals("This file has no audio tracks.", tabs.getToolTipTextAt(1))
            assertEquals(tabs.getToolTipTextAt(1), tabs.getToolTipTextAt(2))
            val volume = descendants(editor.component).filterIsInstance<MediaSlider>().single { it.accessibleContext.accessibleName == "Volume" }
            val mute = descendants(editor.component).filterIsInstance<JCheckBox>().single { it.text == "Mute" }
            assertFalse(volume.isEnabled); assertFalse(mute.isEnabled)
            assertEquals(Cursor.DEFAULT_CURSOR, volume.cursor.type)
            val options = descendants(editor.component).filterIsInstance<JButton>().single { it.text == "⋯" }
            assertFalse(descendants(options.componentPopupMenu).filterIsInstance<JSlider>().single().isEnabled)
            tabs.setSize(800, 500); tabs.doLayout()
            for (index in listOf(0, 1, 3)) {
                val bounds = tabs.getBoundsAt(index)
                tabs.dispatchEvent(MouseEvent(tabs, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 0, false))
                assertEquals(if (index == 1) Cursor.DEFAULT_CURSOR else Cursor.HAND_CURSOR, tabs.cursor.type)
            }
            assertTrue(descendants(editor.component).filterIsInstance<SpectrumView>().all { it.cursor.type == Cursor.HAND_CURSOR })
            assertTrue(descendants(editor.component).filterIsInstance<javax.swing.JScrollBar>().filter { it.isEnabled }.all { it.cursor.type == Cursor.HAND_CURSOR })
        } finally { Disposer.dispose(editor) }
    }

    fun testVfsPathRefreshRestoresPausedAndPlayingNativeSessions() {
        val first = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-session-a-", ".wav")
        val second = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-session-b-", ".wav")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "sine=frequency=440:duration=12", "-y", first.toString()))
        Files.copy(first, second, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        var currentPath = first.toString().replace('\\', '/')
        val file = object : LightVirtualFile("session.wav", MediaFileType, "") {
            override fun getPath() = currentPath
            override fun isInLocalFileSystem() = true
        }
        val editor = MediaEditor(project, file)
        fun native() = descendants(editor.component).filterIsInstance<uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent>().singleOrNull()
        fun refresh(path: java.nio.file.Path) {
            ApplicationManager.getApplication().runWriteAction {
                currentPath = path.toString().replace('\\', '/')
                project.messageBus.syncPublisher(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES).after(emptyList())
            }
        }
        try {
            await("initial source session") { native() != null && descendants(editor.component).filterIsInstance<JButton>().any { it.accessibleContext.accessibleName == "Play" && it.isEnabled } }
            var previous = native()!!
            val slider = descendants(editor.component).filterIsInstance<MediaSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            slider.value = 333_333
            awaitNative("paused position before VFS refresh") { previous.mediaPlayer().status().time() in 3800..4200 }
            refresh(second)
            await("replacement paused session") { native() != null && native() !== previous && descendants(editor.component).filterIsInstance<JButton>().any { it.accessibleContext.accessibleName == "Play" && it.isEnabled } }
            val paused = native()!!
            awaitNative("restored paused source time") { paused.mediaPlayer().status().time() in 3800..4200 && !paused.mediaPlayer().status().isPlaying }
            descendants(editor.component).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Play" }.doClick()
            val playingTime = java.util.concurrent.atomic.AtomicLong()
            awaitNative("playing source before refresh") { playingTime.set(paused.mediaPlayer().status().time()); paused.mediaPlayer().status().isPlaying && playingTime.get() > 4500 }
            previous = paused
            refresh(first)
            await("replacement playing session") { native() != null && native() !== previous }
            val resumed = native()!!
            awaitNative("restored playing source time") { resumed.mediaPlayer().status().isPlaying && resumed.mediaPlayer().status().time() >= playingTime.get() - 250 }
        } finally { Disposer.dispose(editor) }
    }

    fun testProviderDeclinesArchiveImagesAndTracksLocalVfsIdentity() {
        val provider = MediaEditorProvider()
        val root = Files.createTempDirectory(temporary.root.toPath(), "AnyPlayer-vfs-")
        val folder = Files.createDirectory(root.resolve("media"))
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", folder.resolve("image.png").toFile())
        val directory = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(folder)!!
        val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(folder.resolve("image.png"))!!
        val oldSource = MediaSource.parse(file.path)
        file.putUserData(MediaEditorProvider.SOURCE, oldSource)
        assertTrue(provider.accept(project, file))
        assertFalse(provider.accept(project, directory))
        assertFalse(provider.accept(project, LightVirtualFile("resource.png", "")))
        java.util.zip.ZipOutputStream(Files.newOutputStream(root.resolve("images.jar"))).use {
            it.putNextEntry(java.util.zip.ZipEntry("image.png")); it.write(byteArrayOf(1)); it.closeEntry()
        }
        val archive = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve("images.jar"))!!
        val jarImage = com.intellij.openapi.vfs.JarFileSystem.getInstance().getJarRootForLocalFile(archive)!!.findChild("image.png")!!
        assertFalse(provider.accept(project, jarImage))
        val editor = MediaEditor(project, file)
        try {
            val canvas = descendants(editor.component).filterIsInstance<ImageCanvas>().firstOrNull() ?: run {
                await("local image") { descendants(editor.component).any { it is ImageCanvas } }
                descendants(editor.component).filterIsInstance<ImageCanvas>().single()
            }
            await("loaded original path") { canvas.image != null }
            var before = canvas.image
            ApplicationManager.getApplication().runWriteAction { file.rename(this, "renamed.png") }
            assertEquals(java.nio.file.Path.of(file.path), MediaEditorProvider.source(file).localPath)
            assertFalse(oldSource == MediaEditorProvider.source(file))
            await("active source renamed") { canvas.image != null && canvas.image !== before }
            before = canvas.image
            ApplicationManager.getApplication().runWriteAction { directory.rename(this, "moved-media") }
            await("parent directory moved") { canvas.image != null && canvas.image !== before }
        } finally { Disposer.dispose(editor) }
        val reopened = MediaEditor(project, file)
        try { await("reopened renamed source") { descendants(reopened.component).filterIsInstance<ImageCanvas>().any { it.image != null } } }
        finally { Disposer.dispose(reopened) }
    }

    fun testDisabledAudioClearsLiveAndFileSpectrumAndReselects() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-track-", ".wav")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-y", path.toString()))
        val file = LightVirtualFile("tracks.wav", MediaFileType, "").apply { putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString())) }
        val settings = service<PlayerSettings>()
        settings.state.audioView = "Spectrogram"
        val editor = MediaEditor(project, file)
        try {
            await("track selector") { descendants(editor.component).filterIsInstance<JButton>().any { it.text == "⋯" && it.isEnabled } }
            val views = descendants(editor.component).filterIsInstance<SpectrumView>().toList()
            val options = descendants(editor.component).filterIsInstance<JButton>().single { it.text == "⋯" }
            val tracks = descendants(options.componentPopupMenu).filterIsInstance<javax.swing.JMenuItem>().filter { it.text.startsWith("Audio:") }.toList()
            val disable = tracks.single { it.text.contains("Disable", true) }
            val enable = tracks.first { it !== disable }
            val range = descendants(editor.component).filterIsInstance<JComboBox<*>>().single { it.accessibleContext.accessibleName == "Spectrogram time range" }
            for (mode in 0..1) {
                range.selectedIndex = mode
                await("spectrum mode$mode") { views.any { it.frame != null } }
                disable.doClick()
                assertTrue(views.all { it.frame == null && it.message == "No selected audio track is available." })
                enable.doClick()
                await("reselected spectrum mode$mode") { views.any { it.frame != null && it.message == null } }
            }
        } finally { Disposer.dispose(editor) }
    }

    fun testOriginalImagePixelsAreDecodedAt100PercentAfterFitAndPan() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-original-", ".png")
        val sourceImage = BufferedImage(8192, 4096, BufferedImage.TYPE_INT_ARGB)
        val graphics = sourceImage.createGraphics()
        graphics.color = Color.RED; graphics.fillRect(0, 0, 8192, 4096)
        graphics.color = Color.BLUE; graphics.fillRect(4096, 0, 4096, 4096)
        graphics.color = Color.GREEN; graphics.fillRect(4095, 2000, 1, 100)
        graphics.dispose()
        sourceImage.setRGB(4096, 2049, 0)
        ImageIO.write(sourceImage, "png", path.toFile())
        sourceImage.flush()
        val file = LightVirtualFile("original.png", MediaFileType, "").apply { putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString())) }
        val editor = MediaEditor(project, file)
        try {
            await("large image preview") { descendants(editor.component).filterIsInstance<ImageCanvas>().any { it.image != null } }
            val canvas = descendants(editor.component).filterIsInstance<ImageCanvas>().single()
            assertEquals(4096, canvas.image!!.width)
            canvas.setSize(160, 90)
            val original = descendants(editor.component).filterIsInstance<JButton>().single { it.text == "100%" }
            val fit = descendants(editor.component).filterIsInstance<JButton>().last { it.text == "Fit" }
            original.doClick()
            await("original region") { canvas.detail != null && canvas.detailMessage == null }
            val pixel = canvas.detail!!.pixels.getRGB(79, 45)
            assertTrue("One source-pixel green line must survive100%", Color(pixel).green > 200 && Color(pixel).red < 50)
            assertEquals(0, canvas.detail!!.pixels.getRGB(80, 46) ushr 24)
            val painted = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
            painted.createGraphics().apply { canvas.paint(this); dispose() }
            assertTrue("Transparent source pixels must reveal the checkerboard, not the reduced preview", Color(painted.getRGB(80, 46)).red > 150)
            val oldBounds = canvas.detail!!.bounds
            canvas.dispatchEvent(MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 80, 45, 1, false, MouseEvent.BUTTON1))
            canvas.dispatchEvent(MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 40, 45, 1, false, MouseEvent.BUTTON1))
            canvas.dispatchEvent(MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 40, 45, 1, false, MouseEvent.BUTTON1))
            await("panned source region") { canvas.detail?.bounds?.x == oldBounds.x + 40 }
            fit.doClick(); assertNull(canvas.detail)
            original.doClick()
            await("original pixels after Fit") { canvas.detail != null && canvas.detailMessage == null }
        } finally { Disposer.dispose(editor) }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 30_000_000_000L
        while (!condition()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            if (System.nanoTime() > until) fail("Editor: $description did not become ready")
            Thread.sleep(20)
        }
    }

    private fun awaitNative(description: String, condition: () -> Boolean) {
        val check = com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().submit {
            val until = System.nanoTime() + 25_000_000_000L
            while (!condition()) {
                if (System.nanoTime() > until) throw AssertionError("Native check timed out: $description")
                Thread.sleep(20)
            }
        }
        await(description) { check.isDone }
        check.get()
    }

    fun testWrappedIdeNoticeGetsCloseAndPersistentOptOut() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-notice-", ".png")
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
        val file = LightVirtualFile("ignored.png", MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val settings = service<PlayerSettings>()
        settings.state.hideIgnoredNotice = false
        val editor = MediaEditor(project, file)
        try {
            assertTrue(com.intellij.openapi.project.DumbAware::class.java.isInstance(editor))
            val ignored = EditorNotificationPanel().text("You are editing a file that is ignored")
            val unrelated = EditorNotificationPanel().text("Another notice")
            val top = JPanel().apply { add(JPanel().apply { add(ignored) }); add(unrelated) }
            JPanel(BorderLayout()).apply { add(editor.component, BorderLayout.CENTER); add(top, BorderLayout.NORTH) }
            await("wrapped notice actions") { descendants(ignored).any { it is InplaceButton } }
            assertTrue(editorNoticePanels(editor.component).contains(ignored))
            assertTrue(descendants(unrelated).none { it is InplaceButton })
            val close = descendants(ignored).filterIsInstance<InplaceButton>().single()
            assertEquals(Cursor.HAND_CURSOR, close.cursor.type)
            close.doClick()
            assertFalse(ignored.isVisible)
            assertFalse(settings.state.hideIgnoredNotice)
            assertTrue(unrelated.isVisible)
            val next = EditorNotificationPanel().text(ignored.text)
            updateIgnoredNotice(next, false, { settings.state.hideIgnoredNotice = true }) { next.isVisible = false }
            descendants(next).filterIsInstance<HyperlinkLabel>().single { it.text == "Don't show again" }.doClick()
            assertTrue(settings.state.hideIgnoredNotice)
            assertFalse(next.isVisible)
        } finally { Disposer.dispose(editor); Files.deleteIfExists(path) }
    }

    fun testNearbyFrameStripKeepsAnchorAndSeeksOnlyOnClick() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-neighbors-", ".mp4")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=red:s=160x90:r=10:d=1.5",
            "-f", "lavfi", "-i", "color=blue:s=160x90:r=10:d=1.5", "-filter_complex", "[0:v][1:v]concat=n=2:v=1:a=0[v]", "-map", "[v]", "-c:v", "libx264", "-preset", "ultrafast", "-y", path.toString()))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(NativeRuntime.tool("ffprobe"), source)
        val local = LocalPreviewWindow(NativeRuntime.tool("ffmpeg"), source, info.video!!, info.analysisOrigin, 3.0)
        val slider = MediaSlider(0, 1_000_000, 0)
        var selected: Double? = null
        val preview = TimelinePreview(slider, { 3.0 }, { true }, { null }, { local }, { selected = it })
        try {
            slider.onPreview!!(500_000, 100)
            await("local frame strip") { descendants(preview.component).filterIsInstance<JButton>().count { it.isEnabled && it.icon != null } == 5 }
            val buttons = descendants(preview.component).filterIsInstance<JButton>().toList()
            val labels = buttons.map { it.text }
            assertEquals(listOf("00:01.3", "00:01.4", "00:01.5", "00:01.6", "00:01.7"), labels)
            val red = local.refined(1.3)!!.image
            val blue = local.refined(1.7)!!.image
            assertTrue(Color(red.getRGB(40, 40)).red > 200)
            assertTrue(Color(blue.getRGB(40, 40)).blue > 200)
            slider.onPreviewExit!!()
            buttons.last().dispatchEvent(MouseEvent(buttons.last(), MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 2, 2, 0, false))
            assertNull(selected)
            assertEquals(labels, buttons.map { it.text })
            val grace = System.nanoTime() + 300_000_000L
            await("preview exit grace") { System.nanoTime() >= grace }
            assertTrue(descendants(preview.component).single { it.accessibleContext.accessibleName == "Nearby frames" }.isVisible)
            buttons.last().doClick()
            assertEquals(1.7, selected!!, .001)
            assertEquals(0, slider.value)
            preview.hide()
            slider.onPreview!!(100_000, 40)
            slider.onPreview!!(800_000, 300)
            val label = descendants(preview.component).filterIsInstance<javax.swing.JLabel>().single { it.accessibleContext.accessibleName == "Video frame preview" }
            await("latest indexed hover") { label.icon != null && descendants(preview.component).filterIsInstance<javax.swing.JLabel>().any { it.text == "00:02.400" } }
            val latest = label.icon as javax.swing.ImageIcon
            val rendered = BufferedImage(latest.iconWidth, latest.iconHeight, BufferedImage.TYPE_INT_RGB)
            rendered.createGraphics().apply { latest.paintIcon(label, this, 0, 0); dispose() }
            assertTrue(Color(rendered.getRGB(rendered.width / 2, rendered.height / 2)).blue > 200)
            assertEquals(1.7, selected!!, .001)
            preview.component.setSize(preview.component.preferredSize)
            fun layout(component: Component) { if (component is Container) { component.doLayout(); component.components.forEach(::layout) } }
            layout(preview.component)
            assertTrue(preview.component.width <= 480)
            val capture = BufferedImage(preview.component.width, preview.component.height, BufferedImage.TYPE_INT_RGB)
            capture.createGraphics().apply { preview.component.paint(this); dispose() }
            val output = java.nio.file.Path.of("build/reports/ui/timeline-preview.png")
            Files.createDirectories(output.parent)
            ImageIO.write(capture, "png", output.toFile())
        } finally { preview.dispose(); local.close(); Files.deleteIfExists(path) }
    }

    fun testScrubbingShowsTheTargetFrameBeforeReleaseAndCtrlArrowsStepFrames() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-scrub-", ".mp4")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=red:s=160x90:r=4:d=1.5",
            "-f", "lavfi", "-i", "color=blue:s=160x90:r=4:d=1.5", "-filter_complex", "[0:v][1:v]concat=n=2:v=1:a=0[v]", "-map", "[v]", "-c:v", "libx264", "-preset", "ultrafast", "-y", path.toString()))
        val file = LightVirtualFile("scrub.mp4", MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val editor = MediaEditor(project, file)
        try {
            await("scrub player") { descendants(editor.component).filterIsInstance<JButton>().any { (it.accessibleContext.accessibleName in setOf("Play", "Pause")) && it.isEnabled } }
            val native = descendants(editor.component).filterIsInstance<uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent>().single()
            fun clickVideo(target: Component, button: Int = MouseEvent.BUTTON1) {
                for (event in listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                    target.dispatchEvent(MouseEvent(target, event, System.currentTimeMillis(), 0, 40, 40, 1, false, button))
                }
            }
            assertEquals(Cursor.HAND_CURSOR, native.videoSurfaceComponent().cursor.type)
            clickVideo(native.videoSurfaceComponent())
            awaitNative("click video starts playback") { native.mediaPlayer().status().isPlaying }
            clickVideo(native.videoSurfaceComponent())
            awaitNative("click video pauses playback") { !native.mediaPlayer().status().isPlaying }
            clickVideo(native.videoSurfaceComponent(), MouseEvent.BUTTON3)
            val slider = descendants(editor.component).filterIsInstance<MediaSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            slider.valueIsAdjusting = true
            slider.value = 500_000
            awaitNative("native seek while dragging") {
                val actual = native.mediaPlayer().status().time()
                actual in 1490..1800 && !native.mediaPlayer().status().isPlaying
            }
            val surface = descendants(editor.component).filterIsInstance<ImageCanvas>().firstOrNull() ?: run {
                await("stop frame canvas") { descendants(editor.component).any { it is ImageCanvas } }
                descendants(editor.component).filterIsInstance<ImageCanvas>().single()
            }
            surface.setSize(160, 90)
            await("blue stop frame while still dragging") {
                val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()
                try { surface.paint(graphics) } finally { graphics.dispose() }
                val color = Color(image.getRGB(80, 45))
                color.blue > 150 && color.red < 80
            }
            assertTrue(slider.valueIsAdjusting)
            slider.valueIsAdjusting = false
            fun action(name: String) = editor.component.actionMap.get(name).actionPerformed(java.awt.event.ActionEvent(editor.component, 0, name))
            action("frame-back")
            await("Ctrl+Left selects one frame") { slider.value in 415_000..418_000 }
            await("Ctrl+Left presents the preceding red frame") {
                val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()
                try { surface.paint(graphics) } finally { graphics.dispose() }
                val color = Color(image.getRGB(80, 45))
                color.red > 150 && color.blue < 80
            }
            action("frame-forward")
            await("Ctrl+Right selects one frame") { slider.value in 499_000..501_000 }
            await("Ctrl+Right presents the following blue frame") {
                val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()
                try { surface.paint(graphics) } finally { graphics.dispose() }
                val color = Color(image.getRGB(80, 45))
                color.blue > 150 && color.red < 80
            }
            assertEquals("frame-forward", slider.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke("ctrl RIGHT")))
            assertEquals("seek-forward", slider.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke("RIGHT")))
            assertEquals(Cursor.HAND_CURSOR, surface.cursor.type)
            clickVideo(surface)
            awaitNative("click stop frame resumes playback") { native.mediaPlayer().status().isPlaying }
            await("video replaces stop frame after resuming") { !surface.isVisible }
            clickVideo(native.videoSurfaceComponent())
            awaitNative("resumed video can be paused again") { !native.mediaPlayer().status().isPlaying }
        } finally {
            Disposer.dispose(editor)
            await("released scrub fixture") { try { Files.deleteIfExists(path); true } catch (_: java.io.IOException) { false } }
        }
    }

    fun testIgnoredFileNoticeCanBeDismissedOnlyForThisViewer() {
        val ignored = EditorNotificationPanel().text("You are editing a file that is ignored")
        val other = EditorNotificationPanel().text("A different notification")
        val remaining = mutableListOf(ignored, other)
        updateIgnoredNotice(ignored, false) { remaining.remove(ignored) }
        updateIgnoredNotice(ignored, false) { fail("Adding a close button twice must not dismiss the notice") }
        updateIgnoredNotice(other, false) { fail("An unrelated notice must not be dismissed") }
        descendants(ignored).filterIsInstance<InplaceButton>().single().doClick()
        assertFalse(remaining.contains(ignored))
        assertTrue(remaining.contains(other))
        assertTrue(descendants(other).none { it is InplaceButton })
        var reappeared = true
        updateIgnoredNotice(EditorNotificationPanel().text(ignored.text), true) { reappeared = false }
        assertFalse(reappeared)
    }

    fun testReportedMediaOpensLiveSpectrumOverviewAndWaveform() {
        val fixture = System.getProperty("anyplayer.mediaFixture") ?: run {
            println("mediaFixture not supplied: optional fixture check not run.")
            return
        }
        val path = java.nio.file.Path.of(fixture)
        assertTrue(Files.isRegularFile(path))
        val source = MediaSource.parse(path.toString())
        val info = MediaProbe.inspect(NativeRuntime.tool("ffprobe"), source)
        if (info.video == null || info.audio.isEmpty() || (info.duration ?: 0.0) <= 1.0) {
            println("mediaFixture lacks finite video/audio longer than one second: optional fixture check not run.")
            return
        }
        val file = LightVirtualFile(path.fileName.toString(), MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val settings = service<PlayerSettings>()
        settings.state.showAnalysis = true
        val editor = MediaEditor(project, file)
        try {
            await("VLC playback") { descendants(editor.component).filterIsInstance<JButton>().any { (it.accessibleContext.accessibleName in setOf("Play", "Pause")) && it.isEnabled } }
            editor.component.setSize(480, 640)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            fun layout(component: Component) {
                if (component is Container) { component.doLayout(); component.components.forEach(::layout) }
            }
            layout(editor.component)
            val initialControls = descendants(editor.component).filter {
                it is JSlider && it.accessibleContext.accessibleName == "Volume" ||
                    it is JButton && it.text == "⋯" ||
                    it is JComboBox<*> && it.toolTipText == "Playback speed"
            }.toList()
            assertEquals(3, initialControls.size)
            for (label in listOf("−10", "+10")) {
                val button = descendants(editor.component).filterIsInstance<JButton>().single { it.text == label }
                assertTrue(button.toolTipText.contains("10 seconds"))
                assertTrue(button.isEnabled)
                val point = SwingUtilities.convertPoint(button, button.width / 2, button.height / 2, editor.component)
                assertSame("Step button must remain clickable at 480 pixels", button,
                    SwingUtilities.getDeepestComponentAt(editor.component, point.x, point.y))
            }
            initialControls.forEach { control ->
                assertTrue("Initial control must be enabled: $control", control.isEnabled)
                if (!control.isVisible) return@forEach
                val point = SwingUtilities.convertPoint(control, control.width / 2, control.height / 2, editor.component)
                val hit = SwingUtilities.getDeepestComponentAt(editor.component, point.x, point.y)
                assertTrue("Initial control is covered: $control; hit $hit", hit === control || hit != null && SwingUtilities.isDescendingFrom(hit, control))
            }
            val options = initialControls.filterIsInstance<JButton>().single()
            assertNotNull("Track menu must be ready for the first click", options.componentPopupMenu)
            assertFalse("The compact layout must move secondary controls to options", initialControls.filterIsInstance<JSlider>().single().isVisible)
            assertTrue(descendants(options.componentPopupMenu).filterIsInstance<JSlider>().any { it.accessibleContext.accessibleName == "Volume in playback options" })
            val native = descendants(editor.component).filterIsInstance<uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent>().single().mediaPlayer()
            val durationMillis = java.util.concurrent.atomic.AtomicLong()
            val fixtureSeekable = java.util.concurrent.atomic.AtomicBoolean()
            awaitNative("fixture capabilities") { durationMillis.set(native.status().length()); fixtureSeekable.set(native.status().isSeekable); true }
            if (!fixtureSeekable.get() || durationMillis.get() <= 1000) {
                println("mediaFixture lacks seekability/finite native duration: optional fixture check not run.")
                return
            }
            fun click(slider: JSlider, x: Int) {
                val paint = BufferedImage(slider.width, slider.height, BufferedImage.TYPE_INT_RGB).createGraphics()
                try { slider.paint(paint) } finally { paint.dispose() }
                for (event in listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                    slider.dispatchEvent(MouseEvent(slider, event, System.currentTimeMillis(), 0, x, slider.height / 2, 1, false, MouseEvent.BUTTON1))
                }
            }
            val volume = initialControls.filterIsInstance<JSlider>().single()
            val popupVolume = descendants(options.componentPopupMenu).filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Volume in playback options" }
            popupVolume.setSize(160, 24)
            click(popupVolume, popupVolume.width / 3)
            assertTrue("Volume must react before changing tabs: ${volume.value}", volume.value in 20..45)
            assertEquals(volume.value, settings.state.volume)
            val mute = descendants(editor.component).filterIsInstance<JCheckBox>().single { it.text == "Mute" }
            if (!mute.isSelected) mute.doClick()
            val play = descendants(editor.component).filterIsInstance<JButton>().single { (it.accessibleContext.accessibleName in setOf("Play", "Pause")) }
            play.doClick()
            awaitNative("audio output") { native.status().isPlaying }
            play.doClick()
            awaitNative("pause before seeking") { !native.status().isPlaying }
            val speed = initialControls.filterIsInstance<JComboBox<*>>().single()
            speed.selectedItem = 1.25f
            awaitNative("initial playback speed selection") { kotlin.math.abs(native.status().rate() - 1.25f) < .01f }
            val position = descendants(editor.component).filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            position.valueIsAdjusting = true
            position.value = 500_000
            val heldUntil = System.nanoTime() + 400_000_000L
            while (System.nanoTime() < heldUntil) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20) }
            assertEquals("The timer must not overwrite a drag before mouse release", 500_000, position.value)
            awaitNative("drag preview before release") { kotlin.math.abs(native.status().time() - durationMillis.get() / 2.0) < 300 && !native.status().isPlaying }
            position.valueIsAdjusting = false
            awaitNative("paused seek") { kotlin.math.abs(native.status().time() - durationMillis.get() / 2.0) < 300 }
            val settledUntil = System.nanoTime() + 400_000_000L
            while (System.nanoTime() < settledUntil) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20) }
            assertTrue("Native callbacks must not undo a completed seek", position.value in 480_000..520_000)
            click(position, position.width * 3 / 4)
            val selectedTime = position.value / 1_000_000.0 * durationMillis.get()
            awaitNative("timeline track click") { kotlin.math.abs(native.status().time() - selectedTime) < 300 }
            val tabs = descendants(editor.component).filterIsInstance<JTabbedPane>().first { it.getTitleAt(0) == "Preview" }
            val range = descendants(editor.component).filterIsInstance<JComboBox<*>>().single { it.accessibleContext.accessibleName == "Spectrogram time range" }
            range.selectedIndex = 0
            tabs.selectedIndex = 1
            assertFalse("Compact spectrogram controls live in options", range.isVisible)
            val spectrum = tabs.getComponentAt(1) as SpectrumView
            await("live spectrum") { spectrum.live && spectrum.frame != null && spectrum.message == null }
            val cancel = descendants(editor.component).filterIsInstance<JButton>().single { it.text == "Cancel analysis" }
            cancel.doClick()
            assertEquals("Analysis cancelled", spectrum.message)
            assertTrue(play.isEnabled)
            await("resume analysis action") { cancel.text == "Resume analysis" }
            cancel.doClick()
            await("resumed live spectrum") { spectrum.frame != null && spectrum.message == null }
            range.selectedIndex = 1
            await("full-file spectrum") { !spectrum.live && spectrum.frame != null && spectrum.message == null }
            tabs.selectedIndex = 2
            assertFalse("Spectrogram range does not apply to waveform", range.isVisible)
            val waveform = descendants(editor.component).filterIsInstance<WaveformView>().single()
            await("waveform") { waveform.image != null }
            assertEquals(1600, waveform.image!!.width)
            assertEquals(durationMillis.get() * 1000.0, waveform.viewEndMicros.toDouble(), 1000.0)
        } finally { Disposer.dispose(editor) }
    }

    fun testPluginSharesTheAlreadyLoadedIdeJna() {
        val ideNative = Class.forName("com.sun.jna.Native", true, ApplicationManager::class.java.classLoader)
        assertTrue(ideNative.getField("POINTER_SIZE").getInt(null) > 0)
        val plugin = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("me.romanovsky.anyplayer")))
        assertTrue("The plugin must not ship a second copy of the IDE's JNA", Files.list(plugin.pluginPath.resolve("lib")).use { files ->
            files.noneMatch { it.fileName.toString().startsWith("jna-") }
        })
        val pluginNative = Class.forName("com.sun.jna.Native", true, plugin.pluginClassLoader)
        assertSame("A second JNA loader cannot reuse the IDE's native dispatcher", ideNative, pluginNative)
    }

    fun testAudioWaveformLoadsZoomsAndRestoresPreferences() {
        val settings = service<PlayerSettings>()
        settings.loadState(PlayerSettings.Options(volume = 22, muted = true, speed = 1.5f, audioView = "Waveform", splitChannels = true))
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-waveform-", ".wav")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-ac", "2", "-y", path.toString()))
        val file = LightVirtualFile("audio.wav", MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val editor = MediaEditor(project, file)
        try {
            val view = descendants(editor.component).filterIsInstance<WaveformView>().single()
            fun awaitImage() {
                val until = System.nanoTime() + 20_000_000_000L
                while (view.image == null) {
                    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                    if (System.nanoTime() > until) fail("Waveform did not load: ${view.placeholderText}")
                    Thread.sleep(20)
                }
            }
            awaitImage()
            assertTrue(descendants(editor.component).filterIsInstance<JTabbedPane>().any { it.getTitleAt(it.selectedIndex) == "Waveform" })
            assertTrue(descendants(editor.component).filterIsInstance<JCheckBox>().single { it.text == "Mute" }.isSelected)
            assertEquals(1.5f, descendants(editor.component).filterIsInstance<JComboBox<*>>().single { it.toolTipText == "Playback speed" }.selectedItem)
            val originalWidth = view.viewEndMicros - view.viewStartMicros
            descendants(editor.component).filterIsInstance<JButton>().single { it.text == "Zoom in" }.doClick()
            awaitImage()
            assertTrue(view.viewEndMicros - view.viewStartMicros < originalWidth)
            val follow = descendants(editor.component).filterIsInstance<JCheckBox>().single { it.text == "Follow" }
            if (!follow.isSelected) follow.doClick()
            val seek = descendants(editor.component).filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            seek.value = 900_000
            await("waveform follows the seeked playhead") { view.viewStartMicros > 0 && view.viewEndMicros >= 1_800_000 && view.image != null }
            val scroll = descendants(editor.component).filterIsInstance<javax.swing.JScrollBar>().single { it.accessibleContext.accessibleName == "Waveform time range" }
            scroll.value = 0
            assertFalse(follow.isSelected)
            assertFalse(settings.state.followWaveform)
            assertEquals("Waveform", settings.state.audioView)
        } finally {
            Disposer.dispose(editor)
            Files.deleteIfExists(path)
        }
    }

    fun testActualVideoEditorInitializesPlaybackAndClosesDuringBackgroundWork() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-video-", ".mp4")
        ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=12:duration=3", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-y", path.toString()))
        val file = LightVirtualFile("video.mp4", MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val editor = MediaEditor(project, file)
        try {
            val until = System.nanoTime() + 20_000_000_000L
            var play: JButton? = null
            while (play?.isEnabled != true) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                play = descendants(editor.component).filterIsInstance<JButton>().firstOrNull { (it.accessibleContext.accessibleName in setOf("Play", "Pause")) }
                if (System.nanoTime() > until) fail("The actual platform editor did not initialize its packaged media player.")
                Thread.sleep(20)
            }
            val analysis = descendants(editor.component).filterIsInstance<JCheckBox>().first { it.text == "Spectrogram" }
            if (!analysis.isSelected) analysis.doClick()
            assertTrue(analysis.isSelected)
            val mute = descendants(editor.component).filterIsInstance<JCheckBox>().first { it.text == "Mute" }
            if (!mute.isSelected) mute.doClick()
            play.doClick()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            val endedBy = System.nanoTime() + 10_000_000_000L
            while (true) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                if (descendants(editor.component).filterIsInstance<javax.swing.JLabel>().any { it.text == "Ended" }) break
                if (System.nanoTime() > endedBy) fail("The video did not finish")
                Thread.sleep(20)
            }
            val position = descendants(editor.component).filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Playback position" }
            assertEquals("EOF must place the thumb at the end of the timeline", position.maximum, position.value)
            val timeLabels = descendants(editor.component).filterIsInstance<javax.swing.JLabel>().mapNotNull { it.text }
                .filter { it.matches(Regex("\\d{2,}:\\d{2}")) }.toList()
            assertEquals("Current and total source time must agree at EOF", 2, timeLabels.size)
            assertEquals(timeLabels.first(), timeLabels.last())
            play.doClick()
            val restartedBy = System.nanoTime() + 5_000_000_000L
            while (play.icon !== com.intellij.icons.AllIcons.Actions.Pause || position.value >= 500_000) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                if (System.nanoTime() > restartedBy) fail("Play after EOF did not restart at the beginning")
                Thread.sleep(20)
            }
        } finally {
            Disposer.dispose(editor)
            val until = System.nanoTime() + 5_000_000_000L
            while (Files.exists(path)) {
                try { Files.deleteIfExists(path) }
                catch (error: java.io.IOException) { if (System.nanoTime() > until) throw error }
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(20)
            }
            assertFalse(editor.isValid)
        }
    }

    fun testMediaFileTypeAndActionAreRegisteredOnCommonPlatform() {
        assertSame(MediaFileType, FileTypeManager.getInstance().getFileTypeByExtension("mp4"))
        assertTrue(MediaFileType.isBinary)
        assertEquals("Open Media\u2026", ActionManager.getInstance().getAction("AnyPlayer.OpenMedia")?.templatePresentation?.text)
    }

    fun testProviderDoesNotReplaceTypeScriptButAcceptsExplicitSources() {
        val provider = MediaEditorProvider()
        val code = LightVirtualFile("source.ts", "const value = 1;")
        assertFalse(provider.accept(project, code))
        val stream = LightVirtualFile("stream", MediaFileType, "")
        stream.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse("https://media.example.test/stream?token=secret-value"))
        assertTrue(provider.accept(project, stream))
    }

    fun testActualImageEditorLoadsAndDisposesWithoutAWindow() {
        val path = Files.createTempFile(temporary.root.toPath(), "AnyPlayer-image-", ".png")
        val pixels = BufferedImage(40, 24, BufferedImage.TYPE_INT_ARGB)
        pixels.createGraphics().apply { color = Color(30, 130, 200, 128); fillRect(0, 0, 40, 24); dispose() }
        ImageIO.write(pixels, "png", path.toFile())
        val file = LightVirtualFile("image.png", MediaFileType, "")
        file.putUserData(MediaEditorProvider.SOURCE, MediaSource.parse(path.toString()))
        val editor = MediaEditor(project, file)
        try {
            val until = System.nanoTime() + 20_000_000_000L
            var canvas: ImageCanvas? = null
            while (canvas?.image == null) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                canvas = descendants(editor.component).filterIsInstance<ImageCanvas>().firstOrNull()
                if (System.nanoTime() > until) fail("The actual editor did not load the generated PNG. Inspect the IDE test log.")
                Thread.sleep(20)
            }
            assertEquals(40, canvas.image!!.width)
            assertEquals(24, canvas.image!!.height)
            assertTrue(canvas.image!!.colorModel.hasAlpha())
            val original = descendants(editor.component).filterIsInstance<JButton>().first { it.text == "100%" }
            original.doClick()
            assertEquals(1.0, canvas.zoom)
        } finally {
            Disposer.dispose(editor)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertFalse(editor.isValid)
            Files.deleteIfExists(path)
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) for (child in component.components) yieldAll(descendants(child))
    }
}
