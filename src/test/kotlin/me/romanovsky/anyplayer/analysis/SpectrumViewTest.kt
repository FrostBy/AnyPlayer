package me.romanovsky.anyplayer.analysis

import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import org.junit.Assert.*
import org.junit.Test

class SpectrumViewTest {
    @Test fun `buffered live spectrum covers both edges while its clock moves between frames`() {
        SwingUtilities.invokeAndWait {
            val view = SpectrumView().apply {
                setSize(723, 200)
                liveWindow = 8.0
                imageWindowSeconds = 8.5
                duration = 30.0
                liveEnd = 14.25
                message = null
                frame = BufferedImage(680, 256, BufferedImage.TYPE_INT_RGB).apply {
                    createGraphics().apply { color = Color.RED; fillRect(0, 0, width, height); dispose() }
                }
            }
            for (position in listOf(9.9, 10.0, 10.1, 10.2)) {
                view.position = position
                val painted = BufferedImage(723, 200, BufferedImage.TYPE_INT_RGB)
                painted.createGraphics().apply { view.paint(this); dispose() }
                assertEquals("No moving blank strip at the left edge", Color.RED.rgb, painted.getRGB(44, 75))
                assertEquals("Decoded look-ahead must cover the right edge", Color.RED.rgb, painted.getRGB(680, 75))
                assertEquals(Color.WHITE.rgb, painted.getRGB(362, 75))
            }
        }
    }

    @Test fun `live cursor stays centered and unavailable future data stays empty`() {
        SwingUtilities.invokeAndWait {
            val view = SpectrumView()
            view.setSize(723, 200)
            view.liveWindow = 8.0
            view.position = 10.0
            view.duration = 27.0
            view.liveEnd = 10.0
            view.message = null
            view.frame = BufferedImage(640, 256, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply { color = Color.RED; fillRect(0, 0, width, height); dispose() }
            }
            val painted = BufferedImage(723, 200, BufferedImage.TYPE_INT_RGB)
            val graphics = painted.createGraphics()
            try { view.paint(graphics) } finally { graphics.dispose() }
            assertEquals(Color.WHITE.rgb, painted.getRGB(362, 75))
            assertEquals(Color.RED.rgb, painted.getRGB(200, 75))
            assertEquals(Color(11, 17, 29).rgb, painted.getRGB(500, 75))
            var target = -1.0
            view.onSeek = { target = it }
            view.dispatchEvent(MouseEvent(view, MouseEvent.MOUSE_CLICKED, 0, 0, 362, 75, 1, false, MouseEvent.BUTTON1))
            assertEquals(10.0, target, .02)
        }
    }
}
