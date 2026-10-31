package dev.anyplayer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.anyplayer.media.PreviewStoryboard
import dev.anyplayer.media.LocalPreviewWindow
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.GridLayout
import java.awt.Image
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

class TimelinePreview(
    private val slider: MediaSlider,
    private val duration: () -> Double,
    private val available: () -> Boolean,
    private val storyboard: () -> PreviewStoryboard?,
    private val localWindow: () -> LocalPreviewWindow?,
    private val select: (Double) -> Unit,
) : Disposable {
    private val image = JBLabel("Loading…", SwingConstants.CENTER).apply {
        preferredSize = JBUI.size(208, 117)
        accessibleContext.accessibleName = "Video frame preview"
    }
    private val time = JBLabel("", SwingConstants.CENTER).apply { accessibleContext.accessibleName = "Preview time" }
    private val neighbors = List(5) {
        JButton().apply {
            preferredSize = JBUI.size(74, 64)
            margin = JBUI.emptyInsets()
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition = SwingConstants.BOTTOM
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isFocusable = false
            addActionListener { (getClientProperty("time") as? Double)?.let(select) }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(event: MouseEvent) {
                    (getClientProperty("time") as? Double)?.let { pointerTime = it; refreshIndexed() }
                }
            })
        }
    }
    private val strip = JPanel(GridLayout(1, 5, JBUI.scale(3), 0)).apply {
        neighbors.forEach(::add)
        isVisible = false
        accessibleContext.accessibleName = "Nearby frames"
    }
    val component = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
        border = JBUI.Borders.empty(6)
        add(image, BorderLayout.CENTER)
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply { add(time, BorderLayout.NORTH); add(strip, BorderLayout.CENTER) }, BorderLayout.SOUTH)
        accessibleContext.accessibleName = "Timeline preview"
    }
    private var popup: JBPopup? = null
    private var pointer = 0
    private var disposed = false
    private var pointerTime = 0.0
    private var anchorTime = 0.0
    private var displayed: BufferedImage? = null
    private val refresh = Timer(60) { refreshIndexed() }
    private val refine = Timer(140) { localWindow()?.focus(anchorTime); refresh.start() }.apply { isRepeats = false }
    private val exit = Timer(220) { hide() }.apply { isRepeats = false }

    init {
        slider.onPreview = ::show
        slider.onPreviewExit = { exit.restart() }
        val hover = object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) { exit.stop() }
            override fun mouseExited(event: MouseEvent) {
                val point = SwingUtilities.convertPoint(event.component, event.point, component)
                if (!component.contains(point)) exit.restart()
            }
        }
        listOf(component, image, time, strip).plus(neighbors).forEach { it.addMouseListener(hover) }
        slider.addPropertyChangeListener("enabled") { if (!slider.isEnabled) hide() }
        slider.addHierarchyListener { if (it.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !slider.isShowing) hide() }
    }

    private fun show(value: Int, x: Int) {
        if (disposed || !available() || duration() <= 0 || storyboard() == null && localWindow() == null) { hide(); return }
        exit.stop()
        val seconds = value.toDouble() / slider.maximum * duration()
        pointerTime = seconds
        anchorTime = (kotlin.math.round(seconds * 10) / 10).coerceIn(0.0, duration())
        pointer = x
        time.text = MediaEditor.formatTime(seconds)
        refine.restart()
        refresh.start()
        refreshIndexed()
        positionPopup()
    }

    private fun display(frame: BufferedImage) {
        if (displayed === frame && image.icon != null) return
        displayed = frame
        val size = image.preferredSize
        val factor = minOf(size.width.toDouble() / frame.width, size.height.toDouble() / frame.height)
        image.icon = ImageIcon(frame.getScaledInstance((frame.width * factor).toInt().coerceAtLeast(1), (frame.height * factor).toInt().coerceAtLeast(1), Image.SCALE_SMOOTH))
        image.text = ""
    }

    private fun refreshIndexed() {
        val index = storyboard()
        val local = localWindow()
        if (index == null && local == null) return
        index?.request(pointerTime)
        val fine = local?.refined(pointerTime)
        val nearby = local?.nearest(pointerTime)?.takeIf { kotlin.math.abs(it.time - pointerTime) <= .15 }
        val frame = fine?.image ?: nearby?.image ?: index?.peek(pointerTime) ?: index?.nearest(pointerTime)
        if (frame != null) display(frame)
        else image.text = if (local?.failure == null && index?.failure == null) "Preparing previews…" else "Preview unavailable"
        time.text = MediaEditor.formatTime(fine?.time ?: pointerTime, milliseconds = true) + if (fine == null && frame != null) " ≈" else ""
        val showStrip = local?.refined(anchorTime) != null
        if (showStrip) neighbors.forEachIndexed { position, button ->
            val target = anchorTime + (position - 2) * .1
            val neighbor = if (target in 0.0..duration()) local.refined(target) else null
            button.isEnabled = neighbor != null
            button.putClientProperty("time", neighbor?.time)
            if (button.getClientProperty("frame") !== neighbor?.image) {
                button.putClientProperty("frame", neighbor?.image)
                button.icon = neighbor?.image?.let {
                    val scale = minOf(JBUI.scale(68).toDouble() / it.width, JBUI.scale(38).toDouble() / it.height)
                    ImageIcon(it.getScaledInstance((it.width * scale).toInt().coerceAtLeast(1), (it.height * scale).toInt().coerceAtLeast(1), Image.SCALE_SMOOTH))
                }
            }
            button.text = if (target in 0.0..duration()) MediaEditor.formatTime(target, true).dropLast(2) else ""
            button.accessibleContext.accessibleName = "Seek to ${MediaEditor.formatTime(target.coerceAtLeast(0.0), true)}"
            button.toolTipText = "Seek to ${MediaEditor.formatTime(target.coerceAtLeast(0.0), true)}"
        }
        if (strip.isVisible != showStrip) { strip.isVisible = showStrip; component.revalidate(); positionPopup() }
    }

    private fun positionPopup() {
        if (!slider.isShowing) return
        val size = component.preferredSize
        val window = SwingUtilities.getWindowAncestor(slider) ?: return
        if (!window.isActive) return
        val anchor = slider.locationOnScreen
        val x = (anchor.x + pointer - size.width / 2).coerceIn(window.x, (window.x + window.width - size.width).coerceAtLeast(window.x))
        val location = Point(x, anchor.y - size.height - JBUI.scale(6))
        if (popup?.isVisible != true) {
            popup?.cancel()
            popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
                .setFocusable(false).setRequestFocus(false).setResizable(false).setMovable(false)
                .setCancelOnClickOutside(false).setCancelOnWindowDeactivation(true).createPopup().also {
                    it.showInScreenCoordinates(slider, location)
                }
        } else { popup?.setSize(size); popup?.setLocation(location) }
    }

    fun hide() {
        refine.stop()
        exit.stop()
        refresh.stop()
        strip.isVisible = false
        popup?.cancel()
        popup = null
    }

    fun reset() { hide(); displayed = null; image.icon = null }
    override fun dispose() { disposed = true; reset(); slider.onPreview = null; slider.onPreviewExit = null }
}
