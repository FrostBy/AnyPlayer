package dev.anyplayer.images

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Area
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent

data class ImageRegion(val bounds: Rectangle, val pixels: BufferedImage)

class ImageCanvas : JComponent() {
    var sourceSize: Dimension? = null
        set(value) { field = value; detail = null; repaint() }
    var detail: ImageRegion? = null
        set(value) { field = value; repaint() }
    var detailMessage: String? = null
        set(value) { field = value; repaint() }
    var onViewport: ((Rectangle, Double) -> Unit)? = null
    var videoMode: Boolean = false
        set(value) { field = value; repaint() }
    var image: BufferedImage? = null
        set(value) { field = value; repaint() }
    var zoom: Double? = null
        set(value) { field = value; offset = Point(); viewportChanged(); repaint() }
    private var offset = Point()
    private var drag: Point? = null

    init {
        isOpaque = true
        val listener = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) { drag = Point(event.x - offset.x, event.y - offset.y) }
            override fun mouseDragged(event: MouseEvent) {
                drag?.let { offset = Point(event.x - it.x, event.y - it.y); viewportChanged(); repaint() }
            }
            override fun mouseReleased(event: MouseEvent) { drag = null }
        }
        addMouseListener(listener)
        addMouseMotionListener(listener)
        addComponentListener(object : ComponentAdapter() { override fun componentResized(event: ComponentEvent) { viewportChanged() } })
        addMouseWheelListener { event ->
            if (videoMode) return@addMouseWheelListener
            val current = zoom ?: image?.let(::fitScale) ?: 1.0
            zoom = (current * if (event.wheelRotation < 0) 1.15 else 1 / 1.15).coerceIn(.05, 8.0)
        }
    }

    private fun fitScale(frame: BufferedImage): Double = minOf(width.toDouble() / (sourceSize?.width ?: frame.width), height.toDouble() / (sourceSize?.height ?: frame.height),
        if (videoMode) Double.MAX_VALUE else 1.0)

    private fun placement(frame: BufferedImage, scale: Double): Rectangle {
        val w = ((sourceSize?.width ?: frame.width) * scale).toInt().coerceAtLeast(1)
        val h = ((sourceSize?.height ?: frame.height) * scale).toInt().coerceAtLeast(1)
        offset.x = offset.x.coerceIn(-maxOf(0, (w - width) / 2), maxOf(0, (w - width) / 2))
        offset.y = offset.y.coerceIn(-maxOf(0, (h - height) / 2), maxOf(0, (h - height) / 2))
        return Rectangle((width - w) / 2 + offset.x, (height - h) / 2 + offset.y, w, h)
    }

    private fun viewportChanged() {
        val size = sourceSize ?: return
        val frame = image ?: return
        val scale = zoom ?: run { detail = null; detailMessage = null; return }
        if (width <= 0 || height <= 0) return
        val area = placement(frame, scale)
        val x = kotlin.math.floor(-area.x / scale).toInt().coerceIn(0, size.width - 1)
        val y = kotlin.math.floor(-area.y / scale).toInt().coerceIn(0, size.height - 1)
        val right = kotlin.math.ceil((width - area.x) / scale).toInt().coerceIn(x + 1, size.width)
        val bottom = kotlin.math.ceil((height - area.y) / scale).toInt().coerceIn(y + 1, size.height)
        onViewport?.invoke(Rectangle(x, y, right - x, bottom - y), scale)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.color = if (videoMode) Color.BLACK else JBColor.PanelBackground
            g.fillRect(0, 0, width, height)
            val tile = 12
            g.color = JBColor(Color(235, 237, 240), Color(40, 42, 46))
            if (!videoMode) for (x in 0..width / tile) for (y in 0..height / tile) if ((x + y) % 2 == 0) g.fillRect(x * tile, y * tile, tile, tile)
            image?.let { frame ->
                val scale = zoom ?: fitScale(frame)
                val area = placement(frame, scale)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                val region = detail?.takeIf { zoom != null && sourceSize != null }
                val bounds = region?.let { Rectangle(area.x + (it.bounds.x * scale).toInt(), area.y + (it.bounds.y * scale).toInt(),
                    kotlin.math.ceil(it.bounds.width * scale).toInt(), kotlin.math.ceil(it.bounds.height * scale).toInt()) }
                val clip = g.clip
                if (bounds != null) g.clip(Area(Rectangle(0, 0, width, height)).apply { subtract(Area(bounds)) })
                g.drawImage(frame, area.x, area.y, area.width, area.height, null)
                g.clip = clip
                if (bounds != null) g.drawImage(region.pixels, bounds.x, bounds.y, bounds.width, bounds.height, null)
            }
            detailMessage?.let { g.color = JBColor.foreground(); g.drawString(it, 12, height - 12) }
        } finally { g.dispose() }
    }
}
