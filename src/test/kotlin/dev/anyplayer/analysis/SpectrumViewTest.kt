package dev.anyplayer.analysis

import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

class SpectrumViewTest {
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
