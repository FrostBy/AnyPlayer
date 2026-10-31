package me.romanovsky.anyplayer.analysis

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JComponent

class SpectrumView : JComponent() {
    var frame: BufferedImage? = null
        set(value) { field = value; repaint() }
    var position = 0.0
    var duration = 0.0
    var sampleRate = 48000
    var live = true
    var liveEnd = 0.0
    var liveWindow = 6.8267
    var message: String? = "Select an audio track to analyze."
        set(value) { field = value; repaint() }
    var onSeek: (Double) -> Unit = {}

    init {
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        preferredSize = JBUI.size(640, 180)
        minimumSize = JBUI.size(200, 90)
        toolTipText = "Source audio before volume and mute. Click to seek."
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                if (frame == null) return
                val fraction = ((event.x - JBUI.scale(42)).toDouble() / (width - JBUI.scale(83))).coerceIn(0.0, 1.0)
                val start = if (live) position - liveWindow / 2 else 0.0
                val end = if (live) position + liveWindow / 2 else duration
                if (end > start) onSeek((start + fraction * (end - start)).coerceIn(0.0, if (duration > 0) duration else Double.MAX_VALUE))
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            val left = JBUI.scale(42)
            val top = JBUI.scale(9)
            val plotWidth = (width - JBUI.scale(83)).coerceAtLeast(1)
            val plotHeight = (height - JBUI.scale(35)).coerceAtLeast(1)
            g.color = JBColor.PanelBackground
            g.fillRect(0, 0, width, height)
            g.color = Color(11, 17, 29)
            g.fillRect(left, top, plotWidth, plotHeight)
            val start = if (live) position - liveWindow / 2 else 0.0
            val end = if (live) position + liveWindow / 2 else duration
            frame?.let {
                val picture = g.create() as Graphics2D
                try {
                    picture.clipRect(left, top, plotWidth, plotHeight)
                    val x = if (live) left + (liveEnd - liveWindow - start) / liveWindow * plotWidth else left.toDouble()
                    picture.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    picture.translate(x, top.toDouble())
                    picture.drawImage(it, 0, 0, plotWidth, plotHeight, null)
                } finally { picture.dispose() }
            }
            g.font = JBUI.Fonts.smallFont()
            g.color = JBColor.GRAY
            for (tick in 0..4) {
                val frequency = sampleRate / 2.0 * (1 - tick / 4.0)
                val label = if (frequency >= 1000) "${(frequency / 1000).toInt()}k" else frequency.toInt().toString()
                g.drawString(label, JBUI.scale(4), top + tick * plotHeight / 4 + g.fontMetrics.ascent)
                g.color = Color(180, 195, 220, 35)
                g.drawLine(left, top + tick * plotHeight / 4, left + plotWidth, top + tick * plotHeight / 4)
                g.color = JBColor.GRAY
            }
            for (tick in 0..4) {
                val time = start + (end - start).coerceAtLeast(0.0) * tick / 4
                if (time < 0 || duration > 0 && time > duration) continue
                val label = "%02d:%02d".format(time.toInt() / 60, time.toInt() % 60)
                val x = left + tick * plotWidth / 4 - if (tick == 4) g.fontMetrics.stringWidth(label) else 0
                g.drawString(label, x, top + plotHeight + JBUI.scale(18))
            }
            if (frame != null && end > start && position in start..end) {
                val x = left + ((position - start) / (end - start) * plotWidth).toInt()
                g.color = Color.WHITE
                g.drawLine(x, top, x, top + plotHeight)
            }
            for ((index, label) in listOf("0", "−24", "−48", "−72", "−96").withIndex()) {
                g.color = JBColor.GRAY
                g.drawString(label, left + plotWidth + JBUI.scale(5), top + g.fontMetrics.ascent + index * (plotHeight - g.fontMetrics.height) / 4)
            }
            g.drawString("dBFS", left + plotWidth + JBUI.scale(4), top + plotHeight + JBUI.scale(18))
            message?.let { text ->
                g.color = Color(11, 17, 29, 220)
                g.fillRect(left, top, plotWidth, plotHeight)
                g.color = Color(220, 224, 232)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.drawString(text.take(90), left + JBUI.scale(12), top + plotHeight / 2)
            }
        } finally { g.dispose() }
    }
}
