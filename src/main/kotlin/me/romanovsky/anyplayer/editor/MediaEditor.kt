package me.romanovsky.anyplayer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.JRootPane
import javax.swing.JScrollBar
import javax.swing.JSlider
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.text.JTextComponent
import me.romanovsky.anyplayer.analysis.SpectrumCache
import me.romanovsky.anyplayer.analysis.SpectrumSession
import me.romanovsky.anyplayer.analysis.SpectrumView
import me.romanovsky.anyplayer.analysis.WaveformAnalysis
import me.romanovsky.anyplayer.analysis.WaveformView
import me.romanovsky.anyplayer.images.AnimationDecoder
import me.romanovsky.anyplayer.images.AnimationLoops
import me.romanovsky.anyplayer.images.AnimationTimeline
import me.romanovsky.anyplayer.images.ImageCanvas
import me.romanovsky.anyplayer.images.ImageRegionDecoder
import me.romanovsky.anyplayer.media.LocalPreviewWindow
import me.romanovsky.anyplayer.media.MediaInformation
import me.romanovsky.anyplayer.media.MediaProbe
import me.romanovsky.anyplayer.media.MediaThumbnail
import me.romanovsky.anyplayer.media.NativeRuntime
import me.romanovsky.anyplayer.media.PreviewStoryboard
import me.romanovsky.anyplayer.media.ProbeResult
import me.romanovsky.anyplayer.media.SourceStream
import me.romanovsky.anyplayer.media.ToolProcess
import me.romanovsky.anyplayer.media.VideoFrameWindow
import me.romanovsky.anyplayer.playback.PlaybackClock
import me.romanovsky.anyplayer.playback.PlaybackCoordinator
import me.romanovsky.anyplayer.playback.PlayerSettings
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.InputEvents

internal fun updateIgnoredNotice(panel: EditorNotificationPanel, dismissed: Boolean, neverShow: (() -> Unit)? = null, close: () -> Unit) {
    if (panel.text !in setOf("You are editing a file that is ignored", "You are editing a file which is ignored")) return
    if (dismissed) close()
    else if (panel.getClientProperty("anyplayer.dismissibleIgnoredNotice") != true) {
        panel.putClientProperty("anyplayer.dismissibleIgnoredNotice", true)
        panel.setCloseAction { close() }
        if (neverShow != null) panel.createActionLabel("Don't show again") { neverShow(); close() }
            .toolTipText = "Hide ignored-file notices in Any Player only"
        pointerCursors(panel)
    }
}

internal fun editorNoticePanels(component: JComponent): List<EditorNotificationPanel> {
    var parent = component.parent
    while (parent != null && parent !is JRootPane) {
        val north = (parent.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.NORTH) as? JComponent
        if (north != null) {
            val notices = UIUtil.findComponentsOfType(north, EditorNotificationPanel::class.java)
            if (notices.isNotEmpty()) return notices
        }
        parent = parent.parent
    }
    return emptyList()
}

private fun pointerCursors(component: Component) {
    if (component is AbstractButton || component is JComboBox<*> || component is JSlider || component is JScrollBar) {
        fun update() { component.cursor = Cursor.getPredefinedCursor(if (component.isEnabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR) }
        update()
        component.addPropertyChangeListener("enabled") { update() }
    }
    if (component is javax.swing.JTabbedPane) {
        component.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val tab = component.indexAtLocation(event.x, event.y)
                component.cursor = Cursor.getPredefinedCursor(if (tab >= 0 && component.isEnabledAt(tab)) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            }
        })
        component.addMouseListener(object : MouseAdapter() { override fun mouseExited(event: MouseEvent) { component.cursor = Cursor.getDefaultCursor() } })
    }
    if (component is Container) component.components.forEach(::pointerCursors)
}

class MediaEditor(private val project: Project, private val virtualFile: VirtualFile) : UserDataHolderBase(), FileEditor, Disposable, DumbAware {
    @Volatile private var source = MediaEditorProvider.source(virtualFile)
    private var restoredPlayback: Pair<Double, Boolean>? = null
    private var imageRegionJob: Future<*>? = null
    private var imageRegionRequest: Pair<java.awt.Rectangle, Double>? = null
    private var imageRegionTicket = 0L
    private val imageRegionDelay = Timer(100) { decodeImageRegion() }.apply { isRepeats = false }
    private val preferences = service<PlayerSettings>().state
    private val root = JPanel(BorderLayout())
    private val tabs = JBTabbedPane()
    private val cards = JPanel(CardLayout())
    private val mediaSurface = JPanel(CardLayout())
    private val messageTitle = JBLabel("Opening media…")
    private val messageText = JTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true; isOpaque = false; rows = 5 }
    private val inlineSpectrum = SpectrumView()
    private val fullSpectrum = SpectrumView()
    private val waveform = WaveformView { seek(it / 1_000_000.0) }
    private val waveformScroll = JScrollBar(JScrollBar.HORIZONTAL)
    private val splitChannels = JCheckBox("Separate channels", preferences.splitChannels)
    private val followWaveform = JCheckBox("Follow", preferences.followWaveform)
    private val analysisControl = JButton("Cancel analysis")
    private val image = ImageCanvas()
    private val properties = DefaultTableModel(arrayOf("Property", "Value"), 0)
    private val report = JTextArea().apply { isEditable = false; font = JBUI.Fonts.smallFont(); lineWrap = false }
    private val transport = JPanel(BorderLayout(0, JBUI.scale(4)))
    private val play = JButton(AllIcons.Actions.Execute)
    private val back = JButton("−10")
    private val forward = JButton("+10")
    private val mute = JCheckBox("Mute", preferences.muted)
    private val volume = MediaSlider(0, 100, preferences.volume)
    private val speed = JComboBox(arrayOf(.5f, 1f, 1.25f, 1.5f, 2f)).apply { selectedItem = preferences.speed }
    private val options = JButton("⋯")
    private val repeat = JComboBox(arrayOf("File loop", "Loop", "Once")).apply { selectedIndex = preferences.animationRepeat }
    private val positionLabel = JBLabel("00:00")
    private val durationLabel = JBLabel("00:00")
    private val status = JBLabel("Paused")
    private val position = MediaSlider(0, 1_000_000, 0)
    private val range = JComboBox(arrayOf("Live window", "Full file")).apply {
        selectedIndex = if (preferences.analysisRange == "File") 1 else 0
        toolTipText = "Spectrogram time range: a moving window around playback, or the entire file."
        accessibleContext.accessibleName = "Spectrogram time range"
    }
    private val analysisToggle = JCheckBox("Spectrogram", preferences.showAnalysis)
    private val analysisPanel = JPanel(BorderLayout())
    private val imageTools = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(5), 0))
    private val bottom = JPanel(BorderLayout())
    private val disposed = AtomicBoolean()
    private val generation = AtomicLong()
    private val analysisGeneration = AtomicLong()
    private val jobs = ConcurrentLinkedQueue<Future<*>>()
    private val nativeQueue = AppExecutorUtil.createBoundedApplicationPoolExecutor("AnyPlayer playback", 1)
    private val propertyChanges = PropertyChangeSupport(this)
    private val coordinator = project.service<PlaybackCoordinator>()
    @Volatile private var player: CallbackMediaPlayerComponent? = null
    private var probe: ProbeResult? = null
    private var spectrum: SpectrumSession? = null
    private var selectedStream: SourceStream? = null
    private var selectedAudioId: Int? = null
    private var selectedSubtitleId: Int? = null
    private var animation: AnimationTimeline? = null
    private var animationDecoder: AnimationDecoder? = null
    private var animationPlaying = false
    private var animationPosition = 0.0
    private var animationAnchor = 0L
    private var animationOrigin = 0.0
    private var animationAwaitingFrame = false
    private var animationCycleLimit: Int? = 1
    private var animationCycles = 0
    private val animationGeneration = AtomicLong()
    private var duration = 0.0
    private var updatingPosition = false
    private var lastDiagnostic = ""
    private var currentMode = "video"
    private var fileSpectrum: BufferedImage? = null
    private var fullAnalysisJob: Future<*>? = null
    private var waveformJob: Future<*>? = null
    private var waveformRequest = 0L
    private var waveformIdentity: Pair<Int, Boolean>? = null
    private var waveformFailed = false
    private var waveformPendingRange: LongRange? = null
    private var waveformTargetRange: LongRange? = null
    private var waveformPendingColumns = 0
    private var updatingWaveformScroll = false
    private var transportAvailable = false
    private var ignoredNoticeDismissed = false
    private val cache = SpectrumCache(Path.of(PathManager.getSystemPath(), "anyplayer", "spectra"))
    @Volatile private var playing = false
    @Volatile private var playbackEnded = false
    @Volatile private var currentTime = 0.0
    @Volatile private var pausedSeekTime: Double? = null
    @Volatile private var seekable = false
    @Volatile private var buffering = 100f
    private var opening = true
    private var playbackFailed = false
    private var compactTransport = false
    private var analysisCancelled = false
    private var feedbackText = ""
    private var feedbackUntil = 0L
    private var cachedAudioTracks = emptyList<TrackDescription>()
    private var cachedSubtitleTracks = emptyList<TrackDescription>()
    private val playbackClock = PlaybackClock()
    private val sourcePosition: Double get() = if (playbackEnded && duration > 0) duration else
        frameStepTime ?: pausedSeekTime ?: if (animation != null) animationPosition else playbackClock.position().let { if (duration > 0) it.coerceIn(0.0, duration) else it }
    private val timer = Timer(80) { tick() }
    private val renderTimer = Timer(16) { renderTimeline() }
    private var scrubbing = false
    private var resumeAfterScrub = false
    private var lastScrubValue = -1
    private var frameWindow = VideoFrameWindow(emptyList(), false)
    private var frameStepTime: Double? = null
    private var pendingFrameSteps = 0
    private var frameBusy = false
    private var frameRequest = 0L
    private var frameJob: Future<*>? = null
    private var storyboard: PreviewStoryboard? = null
    private var localPreview: LocalPreviewWindow? = null
    private var stillFrameVisible = false
    private var stillFrameTime = 0.0
    private var stillFrameRequest = 0L
    private var stillFrameJob: Future<*>? = null
    private var stillFrameError = false
    private var stillFrameBusy = false
    private val stillFrameDelay = Timer(0) { decodeStillFrame() }.apply { isRepeats = false }
    private val timelinePreview = TimelinePreview(position, { duration },
        { currentMode in setOf("video", "animation") && transportAvailable && seekable && probe?.video != null },
        { storyboard }, { localPreview }, { seek(it) })
    private val frameAnalysisDelay = Timer(150) { restartAnalysis() }.apply { isRepeats = false }
    private val videoClick = object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(event) && event.clickCount == 1 && currentMode == "video" &&
                transportAvailable && !scrubbing && !disposed.get()) {
                position.requestFocusInWindow()
                togglePlayback()
            }
        }
    }

    init {
        image.addMouseListener(videoClick)
        image.onViewport = { bounds, scale ->
            val request = bounds to scale
            if (currentMode == "image" && (request != imageRegionRequest || image.detail == null && !imageRegionDelay.isRunning && imageRegionJob?.isDone != false)) {
                imageRegionRequest = request
                imageRegionTicket++
                imageRegionJob?.cancel(true)
                image.detailMessage = "Loading original pixels…"
                imageRegionDelay.restart()
            }
        }
        root.border = JBUI.Borders.empty(8)
        root.minimumSize = JBUI.size(220, 140)
        val preview = JPanel(BorderLayout())
        val message = JPanel(BorderLayout(0, JBUI.scale(10))).apply {
            border = JBUI.Borders.empty(30)
            add(messageTitle, BorderLayout.NORTH)
            add(messageText, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton("Retry").apply { addActionListener { load() } })
                add(JButton("Copy diagnostics").apply { addActionListener { copy(lastDiagnostic) } })
            }, BorderLayout.SOUTH)
        }
        cards.add(message, "message")
        cards.add(mediaSurface, "media")
        preview.add(cards, BorderLayout.CENTER)
        analysisPanel.add(inlineSpectrum, BorderLayout.CENTER)
        analysisPanel.preferredSize = JBUI.size(400, 170)
        analysisPanel.isVisible = false
        preview.add(analysisPanel, BorderLayout.SOUTH)
        tabs.addTab("Preview", preview)
        tabs.addTab("Spectrogram", fullSpectrum)
        tabs.addTab("Waveform", buildWaveform())
        val information = JPanel(BorderLayout(0, JBUI.scale(6)))
        val table = object : JBTable(properties) { override fun isCellEditable(row: Int, column: Int): Boolean = false }.apply {
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            rowHeight = JBUI.scale(24)
            setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
                init { putClientProperty("html.disable", true) }
                override fun getTableCellRendererComponent(table: JTable, value: Any?, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, selected, focus, row, column)
                    font = if (table.model.getValueAt(row, 1) == "") table.font.deriveFont(java.awt.Font.BOLD) else table.font
                    return this
                }
            })
        }
        val infoViews = JBTabbedPane().apply {
            addTab("Properties", JBScrollPane(table))
            addTab("Full report", JBScrollPane(report))
        }
        information.add(JPanel(BorderLayout()).apply {
            add(JBLabel("MediaInfo"), BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(5), 0)).apply {
                add(JButton("Copy value").apply {
                    isEnabled = false
                    table.selectionModel.addListSelectionListener { isEnabled = table.selectedRow >= 0 && properties.getValueAt(table.convertRowIndexToModel(table.selectedRow), 1).toString().isNotEmpty() }
                    addActionListener { if (table.selectedRow >= 0) copy(properties.getValueAt(table.convertRowIndexToModel(table.selectedRow), 1).toString()) }
                })
                add(JButton("Copy report", AllIcons.Actions.Copy).apply { addActionListener { copy(report.text) } })
            }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        information.add(infoViews, BorderLayout.CENTER)
        tabs.addTab("MediaInfo", information)
        val toolbar = JPanel(BorderLayout())
        toolbar.add(JButton("Open Media…").apply { addActionListener { showOpenMediaDialog(project) } }, BorderLayout.WEST)
        toolbar.add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(7), 0)).apply {
            add(analysisToggle)
            add(range)
            add(analysisControl)
        }, BorderLayout.EAST)
        root.add(toolbar, BorderLayout.NORTH)
        root.add(tabs, BorderLayout.CENTER)
        buildTransport()
        imageTools.add(JButton("Fit").apply { addActionListener { cancelImageRegion(); image.zoom = null } })
        imageTools.add(JButton("100%").apply { addActionListener { image.zoom = 1.0 } })
        imageTools.add(JButton("−").apply { addActionListener { image.zoom = ((image.zoom ?: 1.0) / 1.25).coerceAtLeast(.05) } })
        imageTools.add(JButton("+").apply { addActionListener { image.zoom = ((image.zoom ?: 1.0) * 1.25).coerceAtMost(8.0) } })
        bottom.add(transport, BorderLayout.CENTER)
        bottom.add(imageTools, BorderLayout.EAST)
        root.add(bottom, BorderLayout.SOUTH)
        analysisToggle.addActionListener {
            analysisCancelled = false
            preferences.showAnalysis = analysisToggle.isSelected
            analysisPanel.isVisible = analysisToggle.isSelected && currentMode == "video"
            updateAnalysisControls()
            updateAnalysis()
        }
        range.addActionListener { analysisCancelled = false; preferences.analysisRange = if (range.selectedIndex == 0) "Live" else "File"; restartAnalysis() }
        analysisControl.addActionListener {
            if (analysisCancelled) {
                analysisCancelled = false
                if (tabs.selectedIndex == 2) requestWaveform() else restartAnalysis()
            } else {
                analysisCancelled = true
                stopAnalysis()
                stopWaveform()
                for (view in listOf(inlineSpectrum, fullSpectrum)) view.message = "Analysis cancelled"
                waveform.placeholderText = "Analysis cancelled"
            }
        }
        tabs.addChangeListener {
            analysisCancelled = false
            timelinePreview.hide()
            updateAnalysisControls()
            if (currentMode == "audio" && tabs.selectedIndex in 1..2) preferences.audioView = if (tabs.selectedIndex == 2) "Waveform" else "Spectrogram"
            updateAnalysis()
            if (tabs.selectedIndex == 2) requestWaveform() else stopWaveform()
        }
        inlineSpectrum.onSeek = { seek(it) }
        fullSpectrum.onSeek = { seek(it) }
        installKeys()
        root.addComponentListener(object : java.awt.event.ComponentAdapter() { override fun componentResized(event: java.awt.event.ComponentEvent) { updateTransportLayout() } })
        pointerCursors(root)
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (disposed.get() || !virtualFile.isInLocalFileSystem || !virtualFile.isValid ||
                    Path.of(virtualFile.path).toAbsolutePath().normalize() == source.localPath) return
                SwingUtilities.invokeLater {
                    if (!disposed.get() && virtualFile.isValid) {
                        val updated = runCatching { MediaEditorProvider.source(virtualFile) }.getOrElse { showError(it.message ?: "Media source is unavailable."); return@invokeLater }
                        if (updated != source) {
                            restoredPlayback = (if (animation != null) animationPosition else sourcePosition) to (playing || animationPlaying)
                            source = updated
                            load()
                        }
                    }
                }
            }
        })
        timer.start()
        renderTimer.start()
        playbackClock.setSpeed((speed.selectedItem as Float).toDouble())
        load()
    }

    private fun buildTransport() {
        play.toolTipText = "Play / pause (Space in the media editor)"
        back.margin = JBUI.insets(2, 6)
        forward.margin = JBUI.insets(2, 6)
        back.toolTipText = "Seek backward 10 seconds (Left)"
        forward.toolTipText = "Seek forward 10 seconds (Right)"
        options.toolTipText = "Audio tracks and subtitles"
        volume.preferredSize = JBUI.size(65, 24)
        speed.toolTipText = "Playback speed"
        speed.accessibleContext.accessibleName = "Playback speed"
        options.accessibleContext.accessibleName = "Playback options"
        position.toolTipText = "Seek: Left/Right 10 seconds; Ctrl+Left/Right one frame. Hover for a preview."
        positionLabel.toolTipText = "Go to time (Ctrl+G)"
        positionLabel.isFocusable = false
        positionLabel.addMouseListener(object : java.awt.event.MouseAdapter() { override fun mouseClicked(event: java.awt.event.MouseEvent) { if (SwingUtilities.isLeftMouseButton(event)) goToTime() } })
        speed.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, selected, focus)
                text = "$value×"
                return this
            }
        }
        position.accessibleContext.accessibleName = "Playback position"
        volume.accessibleContext.accessibleName = "Volume"
        val seeking = JPanel(BorderLayout(JBUI.scale(7), 0)).apply {
            add(positionLabel, BorderLayout.WEST)
            add(position, BorderLayout.CENTER)
            add(durationLabel, BorderLayout.EAST)
        }
        transport.add(seeking, BorderLayout.NORTH)
        transport.add(JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { add(play); add(back); add(forward); add(status) }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply { add(mute); add(volume); add(repeat); add(speed); add(options) }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        play.addActionListener { togglePlayback() }
        back.addActionListener { if (animation != null) stepAnimation(-1) else seek((sourcePosition - 10).coerceAtLeast(0.0)) }
        forward.addActionListener { if (animation != null) stepAnimation(1) else seek((sourcePosition + 10).coerceAtMost(duration)) }
        position.addChangeListener {
            if (!updatingPosition && transportAvailable && seekable && duration > 0) {
                if (position.valueIsAdjusting) beginScrub()
                else if (scrubbing) finishScrub()
                else seek(position.value / 1_000_000.0 * duration)
            }
        }
        mute.addActionListener { preferences.muted = mute.isSelected; command { it.audio().setMute(mute.isSelected) }; feedback(if (mute.isSelected) "Muted" else "Unmuted") }
        volume.addChangeListener { preferences.volume = volume.value; command { it.audio().setVolume(volume.value) }; feedback("Volume ${volume.value}%") }
        repeat.addActionListener { preferences.animationRepeat = repeat.selectedIndex }
        speed.addActionListener {
            preferences.speed = speed.selectedItem as Float
            playbackClock.setSpeed((speed.selectedItem as Float).toDouble())
            feedback("${speed.selectedItem}×")
            if (animation != null) {
                animationOrigin = animationPosition
                animationAnchor = System.nanoTime()
            } else command { it.controls().setRate(speed.selectedItem as Float) }
        }
        options.addActionListener { showTracks() }
        setTransportEnabled(false)
    }

    private fun buildWaveform(): JComponent {
        waveform.toolTipText = "Selected source audio. Click or drag to seek; mouse wheel scrolls a zoomed range."
        waveformScroll.accessibleContext.accessibleName = "Waveform time range"
        splitChannels.addActionListener { preferences.splitChannels = splitChannels.isSelected; requestWaveform() }
        followWaveform.toolTipText = "Follow the playhead when it leaves the visible range. Manual scrolling stops following."
        followWaveform.addActionListener { preferences.followWaveform = followWaveform.isSelected; if (followWaveform.isSelected) showWaveformPlayhead() }
        waveform.addMouseWheelListener { event ->
            followWaveform.isSelected = false
            preferences.followWaveform = false
            val width = waveform.viewEndMicros - waveform.viewStartMicros
            val (start, end) = WaveformView.panWindow(waveform.viewStartMicros, waveform.viewEndMicros, (event.preciseWheelRotation * width / 8).toLong(), waveform.durationMicros)
            if (start != waveform.viewStartMicros) requestWaveform(start, end)
        }
        waveformScroll.addAdjustmentListener {
            if (!updatingWaveformScroll) { followWaveform.isSelected = false; preferences.followWaveform = false }
            if (!updatingWaveformScroll && !waveformScroll.valueIsAdjusting && waveform.durationMicros > 0) {
                val start = (waveformScroll.value / 1_000_000.0 * waveform.durationMicros).toLong()
                requestWaveform(start, start + waveform.viewEndMicros - waveform.viewStartMicros)
            }
        }
        val tools = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0)).apply {
            fun zoomButton(label: String, factor: Double) = JButton(label).apply {
                addActionListener {
                    val start = waveform.viewStartMicros
                    val end = waveform.viewEndMicros
                    if (end > start) {
                        val anchor = if (waveform.positionMicros in start..end) (waveform.positionMicros - start).toDouble() / (end - start) else .5
                        val (nextStart, nextEnd) = WaveformView.zoomWindow(start, end, factor, anchor, waveform.durationMicros)
                        requestWaveform(nextStart, nextEnd)
                    }
                }
            }
            add(zoomButton("Zoom in", .5))
            add(zoomButton("Zoom out", 2.0))
            add(JButton("Fit").apply { addActionListener { requestWaveform(0, (duration * 1_000_000).toLong()) } })
            add(splitChannels)
            add(followWaveform)
        }
        return JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(tools, BorderLayout.NORTH)
            add(waveform, BorderLayout.CENTER)
            add(waveformScroll, BorderLayout.SOUTH)
        }
    }

    private fun stopWaveform() {
        waveformRequest++
        waveformJob?.cancel(true)
        waveformJob = null
        waveformPendingRange = null
    }

    private fun requestWaveform(start: Long = waveform.viewStartMicros, end: Long = waveform.viewEndMicros) {
        if (analysisCancelled || tabs.selectedIndex != 2) return
        waveformFailed = false
        val info = probe ?: return
        val stream = selectedStream
        if (!seekable || duration <= 0 || stream == null) {
            stopWaveform()
            waveform.image = null
            waveform.placeholderText = "Waveform requires a seekable source and a selected audio track."
            return
        }
        val total = (duration * 1_000_000).toLong().coerceAtLeast(1)
        waveform.durationMicros = total
        val first = start.coerceIn(0, total - 1)
        val last = (if (end > first) end else total).coerceIn(first + 1, total)
        val span = last - first
        val split = splitChannels.isSelected && stream.channels > 1
        val identity = stream.index to split
        if (waveformIdentity != identity) {
            stopWaveform()
            waveform.image = null
            waveformIdentity = identity
        }
        waveformTargetRange = first..last
        val ready = waveform.image
        val covered = ready != null && waveform.imageStartMicros <= first && waveform.imageEndMicros >= last
        if (ready == null || covered) showWaveformRange(first, last)
        splitChannels.isEnabled = stream.channels > 1
        fun sufficient(from: Long, until: Long, columns: Int): Boolean = until > from &&
            columns.toDouble() * span / (until - from) >= minOf(1600.0, span.toDouble() * stream.sampleRate / 1_000_000).coerceAtLeast(1.0) * .99
        waveformPendingRange?.let {
            if (it.first > first || it.last < last || !sufficient(it.first, it.last, waveformPendingColumns)) stopWaveform()
        }
        val margin = maxOf(span / 4, 250_000L)
        val neededStart = (first - margin).coerceAtLeast(0)
        val neededEnd = last + minOf(margin, total - last)
        if (ready != null && waveform.imageStartMicros <= neededStart && waveform.imageEndMicros >= neededEnd &&
            sufficient(waveform.imageStartMicros, waveform.imageEndMicros, ready.width)) { waveform.placeholderText = null; return }
        if (waveformPendingRange != null) return
        stopWaveform()
        val padding = maxOf(span, 1_000_000L)
        val decodeFirst = (first - padding).coerceAtLeast(0)
        val decodeLast = last + minOf(padding, total - last)
        val columns = kotlin.math.ceil((decodeLast - decodeFirst).toDouble() / span * 1600).toInt().coerceIn(1, 33_600)
        waveformPendingRange = decodeFirst..decodeLast
        waveformPendingColumns = columns
        waveform.placeholderText = if (ready == null) "Generating source waveform…" else if (!covered) "Preparing waveform range…" else null
        val ticket = waveformRequest
        val input = source
        waveformJob = background({
            val key = cache.key(input, stream.index, "waveform-stream-v3-$decodeFirst-$decodeLast-$columns-$split-${info.analysisOrigin}")
            cache.read(key) ?: WaveformAnalysis.render(NativeRuntime.tool("ffmpeg"), input, stream, info.analysisOrigin,
                decodeFirst / 1_000_000.0, decodeLast / 1_000_000.0, split, columns).also { cache.write(key, it) }
        }, { decoded ->
            if (waveformRequest == ticket) {
                waveformPendingRange = null
                val target = waveformTargetRange ?: (first..last)
                if (target.first >= decodeFirst && target.last <= decodeLast) {
                    waveform.imageStartMicros = decodeFirst
                    waveform.imageEndMicros = decodeLast
                    waveform.image = decoded
                    showWaveformRange(target.first, target.last)
                    waveform.placeholderText = null
                } else requestWaveform(target.first, target.last)
            }
        }, { error ->
            if (waveformRequest == ticket) {
                waveformPendingRange = null
                waveformFailed = true
                waveform.placeholderText = "Waveform unavailable. Playback can continue."
                waveform.toolTipText = input.redact(error)
            }
        })
    }

    private fun showWaveformRange(first: Long, last: Long) {
        waveform.viewStartMicros = first
        waveform.viewEndMicros = last
        val total = waveform.durationMicros.coerceAtLeast(1)
        updatingWaveformScroll = true
        waveformScroll.setValues((first.toDouble() / total * 1_000_000).toInt(), ((last - first).toDouble() / total * 1_000_000).toInt().coerceAtLeast(1), 0, 1_000_000)
        waveformScroll.isEnabled = last - first < total
        updatingWaveformScroll = false
    }

    private fun installKeys() {
        fun bind(key: String, name: String, operation: () -> Unit) {
            val stroke = KeyStroke.getKeyStroke(key)
            val action = object : AbstractAction() { override fun actionPerformed(event: ActionEvent) {
                if (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner !is JTextComponent) operation()
            } }
            root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, name)
            root.actionMap.put(name, action)
            position.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name)
            position.actionMap.put(name, action)
        }
        bind("SPACE", "play-pause", ::togglePlayback)
        bind("LEFT", "seek-back") { if (animation != null) stepAnimation(-1) else seek((sourcePosition - 10).coerceAtLeast(0.0)) }
        bind("RIGHT", "seek-forward") { if (animation != null) stepAnimation(1) else seek((sourcePosition + 10).coerceAtMost(duration)) }
        bind("ctrl LEFT", "frame-back") { stepFrame(-1) }
        bind("ctrl RIGHT", "frame-forward") { stepFrame(1) }
        bind("ctrl G", "go-to-time", ::goToTime)
        root.isFocusable = true
    }

    private fun <T> background(work: () -> T, success: (T) -> Unit, failure: (String) -> Unit = { showError(it) }, executor: ExecutorService = AppExecutorUtil.getAppExecutorService(), discard: (T) -> Unit = {}): Future<*> {
        jobs.removeIf { it.isDone }
        val ticket = generation.get()
        val job = executor.submit {
            if (disposed.get() || generation.get() != ticket) return@submit
            try {
                val result = work()
                SwingUtilities.invokeLater {
                    if (!disposed.get() && generation.get() == ticket) success(result)
                    else AppExecutorUtil.getAppExecutorService().execute { discard(result) }
                }
            } catch (error: Throwable) {
                if (error is VirtualMachineError) throw error
                val message = source.redact(error.message ?: error.javaClass.simpleName)
                SwingUtilities.invokeLater { if (!disposed.get() && generation.get() == ticket) failure(message) }
            }
        }
        jobs.add(job)
        return job
    }

    private fun load() {
        cancelImageRegion()
        image.sourceSize = null
        image.detailMessage = null
        localPreview?.close()
        localPreview = null
        opening = true
        playbackFailed = false
        buffering = 100f
        analysisCancelled = false
        storyboard?.close()
        storyboard = null
        timelinePreview.reset()
        cancelFrameStep()
        frameWindow = VideoFrameWindow(emptyList(), false)
        scrubbing = false
        cancelStillFrame()
        stillFrameVisible = false
        generation.incrementAndGet()
        jobs.forEach { it.cancel(true) }
        jobs.clear()
        releasePlayer()
        stopAnalysis()
        stopWaveform()
        waveform.image = null
        waveform.viewStartMicros = 0
        waveform.viewEndMicros = 0
        waveformIdentity = null
        waveformTargetRange = null
        waveformFailed = false
        animationDecoder?.close()
        animationDecoder = null
        animation = null
        animationPlaying = false
        fileSpectrum = null
        options.componentPopupMenu = null
        probe = null
        image.image = null
        mediaSurface.removeAll()
        currentTime = 0.0
        playbackClock.reset()
        pausedSeekTime = null
        playbackEnded = false
        selectedAudioId = null
        selectedSubtitleId = null
        seekable = false
        setTransportEnabled(false)
        showMessage("Opening media…", source.description)
        report.text = "Reading MediaInfo…"
        properties.rowCount = 0
        background({ MediaProbe.inspect(NativeRuntime.tool("ffprobe"), source) }, { result ->
            probe = result
            duration = result.duration ?: 0.0
            selectedStream = result.audio.firstOrNull()
            for (index in 1..2) {
                tabs.setEnabledAt(index, result.audio.isNotEmpty())
                tabs.setToolTipTextAt(index, if (result.audio.isEmpty()) "This file has no audio tracks." else if (index == 1) "Inspect the selected source audio spectrum" else "Inspect the selected source audio waveform")
            }
            analysisToggle.isEnabled = result.audio.isNotEmpty()
            range.isEnabled = result.audio.isNotEmpty()
            when {
                result.animation -> loadAnimation(result)
                result.stillImage || source.extension in setOf("png", "jpg", "jpeg", "bmp", "webp", "avif") && result.audio.isEmpty() && !result.animation -> loadImage(result)
                else -> loadPlayer(result)
            }
        })
        background({
            val tool = NativeRuntime.tool("mediainfo")
            val json = ToolProcess.capture(tool, listOf("--Output=JSON", source.argument), source).toString(Charsets.UTF_8)
            val text = ToolProcess.capture(tool, listOf(source.argument), source).toString(Charsets.UTF_8)
            json to text
        }, { (json, text) ->
            report.text = source.redact(text)
            MediaInformation.tracks(json).forEach { track ->
                properties.addRow(arrayOf(track.title, ""))
                track.properties.forEach { (key, value) -> properties.addRow(arrayOf(key, source.redact(value))) }
            }
        }, { error -> report.text = "MediaInfo could not inspect this source.\n\n$error" })
    }

    private fun loadPlayer(result: ProbeResult) {
        currentMode = if (result.video == null) "audio" else "video"
        configureMode()
        background({
            NativeRuntime.loadVlc()
            // The bundled library is explicit; default discovery recursively scans user directories.
            MediaPlayerFactory(null, listOf("--no-video-title-show", "--quiet"))
        }, { factory ->
            val component = CallbackMediaPlayerComponent(factory, null, InputEvents.NONE, true, null)
            component.videoSurfaceComponent().apply {
                addMouseListener(videoClick)
                if (currentMode == "video") {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    toolTipText = "Click to play or pause"
                }
            }
            player = component
            mediaSurface.add(component, "player")
            component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                private fun current() = !disposed.get() && player === component
                override fun playing(mediaPlayer: MediaPlayer) {
                    if (current()) {
                        pausedSeekTime = null
                        playing = true
                        playbackClock.setRunning(buffering >= 99.9f)
                        SwingUtilities.invokeLater {
                            if (current()) {
                                val level = volume.value
                                val muted = mute.isSelected
                                val rate = speed.selectedItem as Float
                                command { it.audio().setVolume(level); it.audio().setMute(muted); it.controls().setRate(rate) }
                            }
                        }
                    }
                }
                override fun paused(mediaPlayer: MediaPlayer) { if (current()) { playing = false; playbackClock.setRunning(false) } }
                override fun stopped(mediaPlayer: MediaPlayer) { if (current()) { playing = false; playbackClock.setRunning(false) } }
                override fun buffering(mediaPlayer: MediaPlayer, cache: Float) { if (current()) { buffering = cache; playbackClock.setRunning(playing && cache >= 99.9f) } }
                override fun finished(mediaPlayer: MediaPlayer) { if (current()) { playing = false; playbackClock.setRunning(false); playbackEnded = true } }
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    if (!current()) return
                    val accepted = playbackClock.sync(newTime / 1000.0)
                    if (accepted && (pausedSeekTime == null || playing)) currentTime = newTime / 1000.0
                    if (accepted && playing && !scrubbing) SwingUtilities.invokeLater {
                        if (current() && playing && !scrubbing && stillFrameVisible) {
                            stillFrameVisible = false
                            (mediaSurface.layout as CardLayout).show(mediaSurface, "player")
                        }
                    }
                }
                override fun lengthChanged(mediaPlayer: MediaPlayer, length: Long) { SwingUtilities.invokeLater { if (current()) duration = (length / 1000.0).coerceAtLeast(0.0) } }
                override fun seekableChanged(mediaPlayer: MediaPlayer, value: Int) { if (current()) seekable = value != 0 }
                override fun error(mediaPlayer: MediaPlayer) { SwingUtilities.invokeLater { if (current()) showError("The media engine could not decode this source. MediaInfo remains available.") } }
            })
            background({
                val active = component.mediaPlayer()
                check(active.media().startPaused(source.argument)) { "The media engine could not open this source." }
                active.audio().setVolume(volume.value)
                active.audio().setMute(mute.isSelected)
                active.controls().setRate(speed.selectedItem as Float)
                val descriptions = active.audio().trackDescriptions()
                Triple(active.status().isSeekable, result.audioStream(active.audio().track(), descriptions.map { it.id() }) ?: result.audio.singleOrNull(), descriptions to active.subpictures().trackDescriptions())
            }, { (canSeek, stream, tracks) ->
                playing = false
                playbackClock.setRunning(false)
                seekable = canSeek
                selectedStream = stream
                cachedAudioTracks = tracks.first
                cachedSubtitleTracks = tracks.second
                options.componentPopupMenu = createTracksPopup(tracks.first, tracks.second)
                showMedia()
                setTransportEnabled(true)
                val targetTab = if (currentMode == "audio") { if (preferences.audioView == "Waveform") 2 else 1 } else tabs.selectedIndex
                if (tabs.selectedIndex != targetTab) tabs.selectedIndex = targetTab
                else { updateAnalysis(); if (targetTab == 2) requestWaveform() }
            }, executor = nativeQueue)
        }, executor = nativeQueue, discard = { it.release() })
    }

    private fun loadImage(result: ProbeResult) {
        currentMode = "image"
        configureMode()
        background({
            val dimensions = result.video
            val scale = minOf(1.0, 4096.0 / (dimensions?.width ?: 4096).coerceAtLeast(1), 4096.0 / (dimensions?.height ?: 4096).coerceAtLeast(1))
            val width = ((dimensions?.width ?: 4096) * scale).toInt().coerceAtLeast(1)
            val height = ((dimensions?.height ?: 4096) * scale).toInt().coerceAtLeast(1)
            val png = ToolProcess.capture(NativeRuntime.tool("ffmpeg"), listOf("-v", "error", "-nostdin", "-i", source.argument, "-frames:v", "1", "-vf", "scale=$width:$height", "-f", "image2pipe", "-c:v", "png", "pipe:1"), source, maxBytes = 64 * 1024 * 1024)
            checkNotNull(ImageIO.read(ByteArrayInputStream(png))) { "The image could not be decoded." }
        }, { decoded ->
            image.image = decoded
            image.sourceSize = java.awt.Dimension(result.video?.width?.takeIf { it > 0 } ?: decoded.width, result.video?.height?.takeIf { it > 0 } ?: decoded.height)
            image.zoom = null
            restoredPlayback = null
            mediaSurface.add(image, "image")
            showMedia()
        })
    }

    private fun decodeImageRegion() {
        val request = imageRegionRequest ?: return
        if (image.zoom == null || currentMode != "image") return
        val ticket = imageRegionTicket
        val input = source
        imageRegionJob = background({ ImageRegionDecoder.read(NativeRuntime.tool("ffmpeg"), input, request.first, request.second) }, { decoded ->
            if (ticket == imageRegionTicket && image.zoom != null) { image.detail = decoded; image.detailMessage = null }
        }, { error ->
            if (ticket == imageRegionTicket && image.zoom != null) { image.detailMessage = "Original pixels unavailable. Fit remains available."; image.toolTipText = error }
        })
    }

    private fun cancelImageRegion() {
        imageRegionDelay.stop()
        imageRegionTicket++
        imageRegionJob?.cancel(true)
        imageRegionRequest = null
    }

    private fun loadAnimation(result: ProbeResult) {
        background({ AnimationTimeline(MediaProbe.animationFrames(NativeRuntime.tool("ffprobe"), source)) to AnimationLoops.read(source) }, { (timeline, loops) ->
            if (timeline.frames.size == 1) { loadImage(result); return@background }
            animation = timeline
            duration = timeline.duration
            animationPosition = 0.0
            animationCycleLimit = loops
            animationCycles = 0
            repeat.toolTipText = if (loops == null) "File loop metadata is unavailable; one cycle will be used." else "Follow file repeat metadata, loop indefinitely, or play once."
            currentMode = "animation"
            seekable = true
            configureMode()
            mediaSurface.add(image, "image")
            showMedia()
            restartAnimation(0)
            setTransportEnabled(true)
        })
    }

    private fun configureMode() {
        image.videoMode = currentMode == "video"
        image.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        image.toolTipText = if (image.videoMode) "Click to play or pause" else null
        val silent = currentMode in setOf("image", "animation")
        imageTools.isVisible = currentMode == "image"
        transport.isVisible = currentMode != "image"
        mute.isVisible = !silent
        volume.isVisible = !silent
        options.isVisible = !silent
        repeat.isVisible = currentMode == "animation"
        back.text = if (currentMode == "animation") "−1 frame" else "−10"
        forward.text = if (currentMode == "animation") "+1 frame" else "+10"
        back.toolTipText = if (currentMode == "animation") "Previous frame (Ctrl+Left)" else "Seek backward 10 seconds (Left)"
        forward.toolTipText = if (currentMode == "animation") "Next frame (Ctrl+Right)" else "Seek forward 10 seconds (Right)"
        back.accessibleContext.accessibleName = back.toolTipText
        forward.accessibleContext.accessibleName = forward.toolTipText
        analysisPanel.isVisible = analysisToggle.isSelected && currentMode == "video" && probe?.audio?.isNotEmpty() == true
        updateAnalysisControls()
        tabs.setEnabledAt(1, !silent && probe?.audio?.isNotEmpty() == true)
        tabs.setEnabledAt(2, !silent && probe?.audio?.isNotEmpty() == true)
        if (silent && tabs.selectedIndex in 1..2) tabs.selectedIndex = 0
        root.revalidate()
        updateTransportLayout()
    }

    private fun togglePlayback() {
        cancelStillFrame()
        pausedSeekTime = null
        cancelFrameStep()
        timelinePreview.hide()
        if (animation != null) {
            if (animationPosition >= duration) {
                animationPosition = 0.0
                animationCycles = 0
                restartAnimation(0)
            }
            animationPlaying = !animationPlaying
            animationOrigin = animationPosition
            animationAnchor = System.nanoTime()
            return
        }
        val component = player ?: return
        val restart = playbackEnded
        if (restart) {
            playbackEnded = false
            currentTime = 0.0
            playbackClock.reset()
            restartAnalysis()
        }
        background({
            if (playing) component.mediaPlayer().controls().setPause(true)
            else {
                if (restart) {
                    reopenPlayback(component.mediaPlayer())
                }
                coordinator.play(component.mediaPlayer())
            }
        }, {}, executor = nativeQueue)
    }

    private fun command(operation: (MediaPlayer) -> Unit) {
        val component = player ?: return
        nativeQueue.execute {
            if (!disposed.get() && component === player) {
                try { operation(component.mediaPlayer()) }
                catch (error: Exception) { SwingUtilities.invokeLater { if (!disposed.get()) showError(error.message ?: "Playback command failed.") } }
            }
        }
    }

    private fun seek(value: Double, frameStep: Boolean = false) {
        if (!seekable || duration <= 0) return
        if (!frameStep) cancelFrameStep()
        val target = value.coerceIn(0.0, duration)
        localPreview?.focus(target)
        if (animation != null) {
            animationPosition = target
            animationOrigin = target
            animationAnchor = System.nanoTime()
            restartAnimation(animation!!.frameAt(target))
        } else {
            currentTime = target
            playbackClock.seek(target)
            val reopen = playbackEnded
            playbackEnded = false
            command {
                if (reopen) reopenPlayback(it)
                setNativePosition(it, target)
            }
            if (currentMode == "video" && !playing) requestStillFrame(target)
            if (frameStep) frameAnalysisDelay.restart() else restartAnalysis()
        }
    }

    private fun stepAnimation(step: Int) {
        val timeline = animation ?: return
        animationPlaying = false
        val index = (timeline.frameAt(animationPosition) + step).coerceIn(0, timeline.frames.lastIndex)
        seek(timeline.frames[index].time)
    }

    private fun reopenPlayback(active: MediaPlayer) {
        check(active.media().startPaused(source.argument)) { "The media engine could not reopen this source." }
        selectedAudioId?.let { active.audio().setTrack(it) }
        selectedSubtitleId?.let { active.subpictures().setTrack(it) }
    }

    private fun setNativePosition(active: MediaPlayer, time: Double) {
        playbackClock.seek(time)
        val paused = !active.status().isPlaying
        val milliseconds = (time * 1000).toLong()
        pausedSeekTime = if (paused) time else null
        active.controls().setTime(milliseconds)
    }

    private fun cancelStillFrame() {
        stillFrameRequest++
        stillFrameBusy = false
        stillFrameDelay.stop()
        stillFrameJob?.cancel(true)
        stillFrameJob = null
        stillFrameError = false
    }

    private fun requestStillFrame(time: Double) {
        stillFrameTime = time
        stillFrameError = false
        if (!stillFrameBusy && !stillFrameDelay.isRunning) stillFrameDelay.start()
    }

    private fun decodeStillFrame() {
        val video = probe?.video ?: return
        if (stillFrameBusy) return
        val ticket = stillFrameRequest
        val requested = stillFrameTime
        val time = requested.coerceAtMost((duration - .05).coerceAtLeast(0.0))
        val scale = mediaSurface.graphicsConfiguration?.defaultTransform
        val sourceWidth = video.width.takeIf { it > 0 } ?: 1280
        val sourceHeight = video.height.takeIf { it > 0 } ?: 720
        val width = minOf(sourceWidth, (mediaSurface.width.takeIf { it > 0 }?.times(scale?.scaleX ?: 1.0) ?: 1280.0).toInt()).coerceIn(1, 3840)
        val height = minOf(sourceHeight, (mediaSurface.height.takeIf { it > 0 }?.times(scale?.scaleY ?: 1.0) ?: 720.0).toInt()).coerceIn(1, 2160)
        stillFrameBusy = true
        stillFrameJob = background({ previewFrame(time, width, height) }, { frame ->
            if (ticket == stillFrameRequest) {
                stillFrameBusy = false
                if (scrubbing || !playing) {
                    if (kotlin.math.abs(requested - stillFrameTime) < .001) {
                        image.image = frame
                        image.zoom = null
                        if (image.parent !== mediaSurface) mediaSurface.add(image, "image")
                        stillFrameVisible = true
                        (mediaSurface.layout as CardLayout).show(mediaSurface, "image")
                    } else requestStillFrame(stillFrameTime)
                }
            }
        }, { error ->
            if (ticket == stillFrameRequest) {
                stillFrameBusy = false
                if (kotlin.math.abs(requested - stillFrameTime) >= .001 && (scrubbing || !playing)) requestStillFrame(stillFrameTime)
                else { stillFrameError = true; status.toolTipText = source.redact(error) }
            }
        })
    }

    private fun previewFrame(time: Double, width: Int, height: Int): BufferedImage {
        val info = checkNotNull(probe)
        val video = checkNotNull(info.video)
        val ffmpeg = NativeRuntime.tool("ffmpeg")
        return try { MediaThumbnail.read(ffmpeg, source, video.index, info.analysisOrigin, time, width, height) }
        catch (error: IllegalStateException) {
            // A seek into the final held frame can have no subsequent timestamp to decode.
            val frames = MediaProbe.videoFrames(NativeRuntime.tool("ffprobe"), source, video.index, info.analysisOrigin, time)
            val previous = frames.times.lastOrNull { it <= time + .0005 } ?: throw error
            MediaThumbnail.read(ffmpeg, source, video.index, info.analysisOrigin, (previous - .0005).coerceAtLeast(0.0), width, height)
        }
    }

    private fun beginScrub() {
        if (scrubbing) return
        cancelFrameStep()
        scrubbing = true
        lastScrubValue = -1
        resumeAfterScrub = if (animation != null) animationPlaying else playing
        animationPlaying = false
        stopAnalysis()
        val reopen = playbackEnded
        playbackEnded = false
        command { if (reopen) reopenPlayback(it); it.controls().setPause(true) }
    }

    private fun finishScrub() {
        scrubbing = false
        val resume = resumeAfterScrub
        seek(position.value / 1_000_000.0 * duration)
        if (animation != null) animationPlaying = resume
        else {
            if (resume) cancelStillFrame()
            command {
                if (resume) { pausedSeekTime = null; coordinator.play(it) }
                else it.controls().setPause(true)
            }
        }
    }

    private fun cancelFrameStep() {
        frameAnalysisDelay.stop()
        frameRequest++
        frameJob?.cancel(true)
        frameJob = null
        frameBusy = false
        pendingFrameSteps = 0
        frameStepTime = null
    }

    private fun stepFrame(direction: Int) {
        timelinePreview.hide()
        if (animation != null) { stepAnimation(direction); return }
        if (currentMode != "video" || !seekable || !transportAvailable) return
        pendingFrameSteps += direction
        if (frameBusy) return
        frameBusy = true
        val ticket = frameRequest
        val active = player?.mediaPlayer() ?: run { frameBusy = false; return }
        val previous = frameStepTime ?: pausedSeekTime
        val reopen = playbackEnded
        playbackEnded = false
        stopAnalysis()
        frameJob = background({
            if (reopen) reopenPlayback(active)
            active.controls().setPause(true)
            previous ?: if (reopen) duration else active.status().time() / 1000.0
        }, { from -> if (ticket == frameRequest) findFrame(from, ticket) }, { error -> frameFailure(error, ticket) }, executor = nativeQueue)
    }

    private fun findFrame(from: Double, ticket: Long) {
        val info = probe ?: return
        val video = info.video ?: return
        val times = frameWindow.times
        val index = times.binarySearch(from + .0005).let { if (it >= 0) it else -it - 2 }
        val target = index + pendingFrameSteps
        val covered = times.isNotEmpty() &&
            (target >= 0 || times.first() <= (video.start - info.analysisOrigin).coerceAtLeast(0.0) + .001) &&
            (target < times.size || frameWindow.atEnd)
        if (!covered) {
            frameJob = background({ MediaProbe.videoFrames(NativeRuntime.tool("ffprobe"), source, video.index, info.analysisOrigin, from) }, {
                if (ticket == frameRequest) { frameWindow = it; applyFrame(from) }
            }, { error -> frameFailure(error, ticket) })
        } else applyFrame(from)
    }

    private fun applyFrame(from: Double) {
        val times = frameWindow.times
        if (times.isEmpty()) { frameBusy = false; pendingFrameSteps = 0; return }
        val index = times.binarySearch(from + .0005).let { if (it >= 0) it else -it - 2 }
        val desired = index + pendingFrameSteps
        if (index < 0 && pendingFrameSteps < 0) { pendingFrameSteps = 0; frameBusy = false; return }
        val target = desired.coerceIn(0, times.lastIndex)
        val remaining = if (desired > times.lastIndex && !frameWindow.atEnd) desired - times.lastIndex
            else if (desired < 0 && times.first() > ((probe?.video?.start ?: 0.0) - (probe?.analysisOrigin ?: 0.0)).coerceAtLeast(0.0) + .001) desired else 0
        pendingFrameSteps = 0
        frameBusy = false
        frameStepTime = times[target]
        seek(times[target], frameStep = true)
        if (remaining != 0) stepFrame(remaining)
    }

    private fun frameFailure(error: String, ticket: Long) {
        if (ticket != frameRequest) return
        frameBusy = false
        pendingFrameSteps = 0
        stillFrameError = true
        lastDiagnostic = source.redact(error)
        status.toolTipText = lastDiagnostic
        status.text = "Frame stepping unavailable"
    }

    private fun restartAnimation(index: Int) {
        val ticket = animationGeneration.incrementAndGet()
        animationDecoder?.close()
        image.image = null
        animationAwaitingFrame = true
        val timeline = animation ?: return
        val video = probe?.video ?: return
        animationDecoder = AnimationDecoder(NativeRuntime.tool("ffmpeg"), source, timeline, video.width, video.height, index) { error ->
            SwingUtilities.invokeLater { if (!disposed.get() && animationGeneration.get() == ticket) showError(error) }
        }
    }

    private fun showTracks() {
        options.componentPopupMenu = createTracksPopup(cachedAudioTracks, cachedSubtitleTracks)
        options.componentPopupMenu?.show(options, 0, options.height)
    }

    private fun createTracksPopup(audio: List<TrackDescription>, subtitles: List<TrackDescription>): JPopupMenu {
            val popup = JPopupMenu()
            popup.add(JMenuItem("Go to time… (Ctrl+G)").apply { isEnabled = seekable && duration > 0; addActionListener { goToTime() } })
            fun choices(title: String, labels: List<String>, selected: Int, choose: (Int) -> Unit) {
                val menu = JMenu(title)
                val group = ButtonGroup()
                labels.forEachIndexed { index, text -> menu.add(JRadioButtonMenuItem(text, selected == index).apply {
                    group.add(this); addActionListener { choose(index) }
                }) }
                popup.add(menu)
            }
            choices("Playback speed", (0 until speed.itemCount).map { "${speed.getItemAt(it)}×" }, speed.selectedIndex) { speed.selectedIndex = it }
            if (currentMode != "animation") {
                val hasAudio = probe?.audio?.isNotEmpty() == true
                val label = JBLabel("Volume ${volume.value}%")
                val slider = MediaSlider(0, 100, volume.value).apply {
                    preferredSize = JBUI.size(160, 24)
                    accessibleContext.accessibleName = "Volume in playback options"
                    isEnabled = hasAudio
                    toolTipText = if (hasAudio) "Playback volume" else "This file has no audio tracks."
                    addChangeListener { volume.value = value; label.text = "Volume $value%" }
                }
                popup.add(JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(4, 10); add(label, BorderLayout.NORTH); add(slider, BorderLayout.CENTER) })
                popup.add(JCheckBoxMenuItem("Mute", mute.isSelected).apply {
                    isEnabled = hasAudio
                    toolTipText = if (hasAudio) "Mute playback" else "This file has no audio tracks."
                    addActionListener { mute.doClick() }
                })
            } else choices("Repeat", (0 until repeat.itemCount).map { repeat.getItemAt(it) }, repeat.selectedIndex) { repeat.selectedIndex = it }
            if (probe?.audio?.isNotEmpty() == true) choices("Spectrogram range", (0 until range.itemCount).map { range.getItemAt(it) }, range.selectedIndex) { range.selectedIndex = it }
            if (currentMode in setOf("video", "animation")) {
                popup.add(JMenuItem("Previous frame (Ctrl+Left)").apply { addActionListener { stepFrame(-1) } })
                popup.add(JMenuItem("Next frame (Ctrl+Right)").apply { addActionListener { stepFrame(1) } })
            }
            if (audio.isNotEmpty() || subtitles.isNotEmpty()) popup.addSeparator()
            for (track in audio) popup.add(javax.swing.JMenuItem("Audio: ${track.description()}").apply { addActionListener {
                selectedAudioId = track.id()
                command { it.audio().setTrack(track.id()) }
                selectedStream = probe?.audioStream(track.id(), audio.map { it.id() })
                fileSpectrum = null
                restartAnalysis()
                waveform.image = null
                if (tabs.selectedIndex == 2) requestWaveform()
            } })
            if (audio.isNotEmpty() && subtitles.isNotEmpty()) popup.addSeparator()
            for (track in subtitles) popup.add(javax.swing.JMenuItem("Subtitles: ${track.description()}").apply { addActionListener { selectedSubtitleId = track.id(); command { it.subpictures().setTrack(track.id()) } } })
            if (player != null) {
            popup.addSeparator()
            popup.add(javax.swing.JMenuItem("Refresh tracks").apply { addActionListener {
                val active = player?.mediaPlayer() ?: return@addActionListener
                background({ active.audio().trackDescriptions() to active.subpictures().trackDescriptions() }, { tracks ->
                    cachedAudioTracks = tracks.first
                    cachedSubtitleTracks = tracks.second
                    options.componentPopupMenu = createTracksPopup(tracks.first, tracks.second)
                    showTracks()
                }, executor = nativeQueue)
            } })
            }
            pointerCursors(popup)
            return popup
    }

    private fun updateAnalysisControls() {
        val available = currentMode in setOf("audio", "video") && probe?.audio?.isNotEmpty() == true
        analysisToggle.isVisible = available && currentMode == "video" && tabs.selectedIndex == 0
        range.isVisible = !compactTransport && available && (tabs.selectedIndex == 1 || tabs.selectedIndex == 0 && analysisPanel.isVisible)
        root.revalidate()
    }

    private fun updateAnalysis() {
        if (analysisCancelled) return
        val requested = tabs.selectedIndex == 1 || tabs.selectedIndex == 0 && analysisPanel.isVisible
        if (!requested) stopAnalysis() else if (spectrum == null && selectedStream != null) restartAnalysis()
    }

    private fun stopAnalysis() {
        analysisGeneration.incrementAndGet()
        fullAnalysisJob?.cancel(true)
        fullAnalysisJob = null
        spectrum?.close()
        spectrum = null
    }

    private fun restartAnalysis() {
        stopAnalysis()
        if (analysisCancelled) return
        val info = probe ?: return
        val stream = selectedStream ?: run {
            fileSpectrum = null
            for (view in listOf(inlineSpectrum, fullSpectrum)) { view.frame = null; view.message = "No selected audio track is available." }
            return
        }
        if (tabs.selectedIndex != 1 && !(tabs.selectedIndex == 0 && analysisPanel.isVisible)) return
        val ticket = analysisGeneration.get()
        for (view in listOf(inlineSpectrum, fullSpectrum)) { view.sampleRate = stream.sampleRate; view.duration = duration; view.message = "Analyzing source audio…"; view.frame = null; view.live = range.selectedIndex == 0 }
        if (range.selectedIndex == 0) {
            spectrum = SpectrumSession(NativeRuntime.tool("ffmpeg"), source, stream, info.analysisOrigin, currentTime, canSeek = seekable) { error ->
                SwingUtilities.invokeLater { if (!disposed.get() && analysisGeneration.get() == ticket) analysisError(error) }
            }
        } else {
            if (!seekable || duration <= 0) { analysisError("A full-file overview is unavailable for this non-seekable stream."); return }
            if (fileSpectrum != null) { showFileSpectrum(); return }
            fullAnalysisJob = background({
                val key = cache.key(source, stream.index)
                cache.read(key) ?: run {
                    val decoded = SpectrumSession.overview(NativeRuntime.tool("ffmpeg"), source, stream, info.analysisOrigin, duration)
                    cache.write(key, decoded)
                    decoded
                }
            }, { decoded -> if (analysisGeneration.get() == ticket) { fileSpectrum = decoded; showFileSpectrum() } }, { error -> if (analysisGeneration.get() == ticket) analysisError(error) })
        }
    }

    private fun showFileSpectrum() {
        for (view in listOf(inlineSpectrum, fullSpectrum)) { view.frame = fileSpectrum; view.message = null; view.live = false }
    }

    private fun analysisError(error: String) {
        lastDiagnostic = source.redact(error)
        for (view in listOf(inlineSpectrum, fullSpectrum)) view.message = "Analysis unavailable. Playback can continue."
        status.toolTipText = lastDiagnostic
    }

    private fun tick() {
        if (disposed.get()) return
        for (panel in editorNoticePanels(root)) {
            updateIgnoredNotice(panel, ignoredNoticeDismissed || preferences.hideIgnoredNotice, { preferences.hideIgnoredNotice = true }) {
                ignoredNoticeDismissed = true
                panel.isVisible = false
                panel.parent?.revalidate()
            }
        }
        if (scrubbing && position.value != lastScrubValue) {
            lastScrubValue = position.value
            val target = position.value / 1_000_000.0 * duration
            if (animation != null) seek(target)
            else { currentTime = target; command { setNativePosition(it, target) }; requestStillFrame(target) }
        }
        val timeline = animation
        if (timeline != null) {
            if (animationPlaying && !animationAwaitingFrame) {
                animationPosition = animationOrigin + (System.nanoTime() - animationAnchor) / 1_000_000_000.0 * (speed.selectedItem as Float)
                if (animationPosition >= timeline.duration) {
                    animationCycles++
                    val limit = when (repeat.selectedIndex) { 1 -> 0; 2 -> 1; else -> animationCycleLimit ?: 1 }
                    if (limit == 0 || animationCycles < limit) { animationPosition = 0.0; animationOrigin = 0.0; animationAnchor = System.nanoTime(); restartAnimation(0) }
                    else { animationPosition = timeline.duration; animationPlaying = false }
                }
            }
            currentTime = animationPosition
            val decoded = animationDecoder?.frameAt(animationPosition)
            if (decoded != null) {
                image.image = decoded.image
                if (animationAwaitingFrame) { animationOrigin = animationPosition; animationAnchor = System.nanoTime() }
                animationAwaitingFrame = false
            } else {
                image.image = null
                animationAwaitingFrame = true
            }
            status.text = if (animationAwaitingFrame) "Decoding frame…" else "Frame ${timeline.frameAt(animationPosition) + 1} / ${timeline.frames.size}"
        } else status.text = when {
            playbackFailed -> "Failed"
            opening -> "Opening…"
            buffering < 99.9f -> "Buffering…"
            playbackEnded -> "Ended"
            stillFrameError -> "Frame error"
            scrubbing || frameBusy || stillFrameDelay.isRunning || stillFrameBusy -> "Seeking…"
            System.nanoTime() < feedbackUntil -> feedbackText
            playing -> "Playing"
            else -> "Paused"
        }
        // VLC can signal EOF before a final timeChanged reaches the exact duration.
        val displayTime = sourcePosition
        play.icon = if (if (timeline != null) animationPlaying else playing) AllIcons.Actions.Pause else AllIcons.Actions.Execute
        val playName = if (if (timeline != null) animationPlaying else playing) "Pause" else "Play"
        play.accessibleContext.accessibleName = playName
        play.toolTipText = "$playName (Space)"
        positionLabel.cursor = Cursor.getPredefinedCursor(if (seekable && transportAvailable) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        positionLabel.text = formatTime(if (position.valueIsAdjusting && duration > 0) position.value / 1_000_000.0 * duration else displayTime, timeline != null || frameStepTime != null)
        durationLabel.text = if (duration > 0) formatTime(duration, timeline != null) else "Live"
        position.isEnabled = transportAvailable && seekable && duration > 0
        analysisControl.text = if (analysisCancelled) "Resume analysis" else "Cancel analysis"
        analysisControl.isVisible = (tabs.selectedIndex in 1..2 || analysisPanel.isVisible) &&
            (analysisCancelled || spectrum != null || fullAnalysisJob?.isDone == false || waveformJob?.isDone == false)
    }

    private fun renderTimeline() {
        if (disposed.get()) return
        val displayTime = sourcePosition
        if (!position.valueIsAdjusting) {
            updatingPosition = true
            position.value = if (duration > 0) (displayTime / duration * 1_000_000).toInt().coerceIn(0, 1_000_000) else 0
            updatingPosition = false
        }
        spectrum?.let { session ->
            session.frameAt(displayTime)?.let { timed ->
                for (view in listOf(inlineSpectrum, fullSpectrum)) {
                    if (view.frame !== timed.image) view.frame = timed.image
                    if (view.message != null) view.message = null
                    view.liveEnd = timed.time + session.lookAheadSeconds
                    view.liveWindow = session.windowSeconds
                }
            }
        }
        for (view in listOf(inlineSpectrum, fullSpectrum)) if (view.position != displayTime) { view.position = displayTime; view.repaint() }
        val micros = (displayTime * 1_000_000).toLong()
        if (waveform.positionMicros != micros) waveform.positionMicros = micros
        if (!waveformFailed && followWaveform.isSelected && tabs.selectedIndex == 2 && waveform.image != null &&
            waveform.viewEndMicros - waveform.viewStartMicros < waveform.durationMicros) showWaveformPlayhead()
    }

    private fun showMessage(title: String, description: String) {
        messageTitle.text = title
        messageText.text = description
        (cards.layout as CardLayout).show(cards, "message")
    }

    private fun showError(error: String) {
        opening = false
        playbackFailed = true
        playing = false
        playbackClock.setRunning(false)
        animationPlaying = false
        lastDiagnostic = source.redact(error)
        setTransportEnabled(false)
        showMessage("Cannot open this media", lastDiagnostic.take(1200))
    }

    private fun showMedia() {
        opening = false
        (cards.layout as CardLayout).show(cards, "media")
        (mediaSurface.layout as CardLayout).show(mediaSurface, if (currentMode in setOf("video", "audio")) "player" else "image")
        mediaSurface.revalidate()
        mediaSurface.repaint()
    }

    private fun setTransportEnabled(enabled: Boolean) {
        transportAvailable = enabled
        for (control in listOf(play, back, forward, mute, volume, speed, options, repeat, position)) control.isEnabled = enabled
        val hasAudio = probe?.audio?.isNotEmpty() == true
        for (control in listOf(mute, volume)) {
            control.isEnabled = enabled && hasAudio
            control.toolTipText = if (probe != null && !hasAudio) "This file has no audio tracks." else if (control === volume) "Playback volume" else "Mute playback"
        }
        if (enabled && storyboard == null && seekable && duration > 0 && currentMode in setOf("video", "animation")) {
            val info = probe
            val video = info?.video
            if (video != null) storyboard = PreviewStoryboard(NativeRuntime.tool("ffmpeg"), source, video.index, info.analysisOrigin, duration,
                Path.of(PathManager.getSystemPath(), "anyplayer", "previews"))
            if (video != null) localPreview = LocalPreviewWindow(NativeRuntime.tool("ffmpeg"), source, video, info.analysisOrigin, duration)
        }
        if (enabled) restoredPlayback?.let { (time, resume) ->
            restoredPlayback = null
            seek(time.coerceIn(0.0, duration))
            if (resume) togglePlayback()
        }
    }

    private fun feedback(text: String) { feedbackText = text; feedbackUntil = System.nanoTime() + 1_000_000_000L }

    private fun updateTransportLayout() {
        compactTransport = root.width in 1 until JBUI.scale(620)
        val silent = currentMode in setOf("image", "animation")
        mute.isVisible = !silent && !compactTransport
        volume.isVisible = !silent && !compactTransport
        speed.isVisible = !compactTransport
        repeat.isVisible = currentMode == "animation" && !compactTransport
        options.isVisible = currentMode != "image"
        status.isVisible = root.width == 0 || root.width >= JBUI.scale(360)
        updateAnalysisControls()
    }

    private fun showWaveformPlayhead() {
        val total = (duration * 1_000_000).toLong()
        val width = waveform.viewEndMicros - waveform.viewStartMicros
        if (total <= 0 || width <= 0 || width >= total) return
        val start = ((sourcePosition * 1_000_000).toLong() - width / 4).coerceIn(0, (total - width).coerceAtLeast(0))
        if (start != waveform.viewStartMicros || start + width != waveform.viewEndMicros) requestWaveform(start, start + width)
    }

    private fun goToTime() {
        if (!seekable || duration <= 0 || !transportAvailable) return
        val value = Messages.showInputDialog(project, "Enter seconds, mm:ss or hh:mm:ss[.mmm]", "Go to Time", null,
            formatTime(sourcePosition, true), object : InputValidator {
                override fun checkInput(inputString: String): Boolean = parseTime(inputString, duration) != null
                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            }) ?: return
        parseTime(value, duration)?.let { seek(it); feedback("Seek ${formatTime(it)}") }
    }

    private fun copy(text: String) { CopyPasteManager.getInstance().setContents(StringSelection(text)) }

    private fun releasePlayer() {
        val old = player ?: return
        player = null
        coordinator.forget(old.mediaPlayer())
        nativeQueue.execute {
            val factory = old.mediaPlayerFactory()
            try { old.mediaPlayer().controls().stop(); old.release() }
            finally { factory.release() }
        }
    }

    override fun dispose() {
        cancelImageRegion()
        image.onViewport = null
        localPreview?.close()
        storyboard?.close()
        cancelStillFrame()
        timelinePreview.dispose()
        cancelFrameStep()
        disposed.set(true)
        generation.incrementAndGet()
        timer.stop()
        renderTimer.stop()
        jobs.forEach { it.cancel(true) }
        jobs.clear()
        stopAnalysis()
        stopWaveform()
        animationDecoder?.close()
        animationGeneration.incrementAndGet()
        releasePlayer()
        nativeQueue.shutdown()
    }

    override fun getComponent(): JComponent = root
    override fun getPreferredFocusedComponent(): JComponent = root
    override fun getName(): String = "Any Player"
    override fun getFile(): VirtualFile = virtualFile
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = !disposed.get() && (source.localPath == null || virtualFile.isValid)
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = propertyChanges.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = propertyChanges.removePropertyChangeListener(listener)

    companion object {
        fun parseTime(input: String, duration: Double): Double? {
            val value = input.trim()
            if (!value.matches(Regex("\\d+(?::[0-5]\\d){0,2}(?:\\.\\d{1,3})?"))) return null
            val seconds = value.split(':').fold(0.0) { total, part -> total * 60 + (part.toDoubleOrNull() ?: return null) }
            return seconds.takeIf { it.isFinite() && it in 0.0..duration }
        }
        fun formatTime(seconds: Double, milliseconds: Boolean = false): String {
            val total = (seconds.coerceAtLeast(0.0) * 1000).toLong()
            val base = "%02d:%02d".format(total / 60000, total / 1000 % 60)
            return if (milliseconds) "$base.%03d".format(total % 1000) else base
        }
    }
}
