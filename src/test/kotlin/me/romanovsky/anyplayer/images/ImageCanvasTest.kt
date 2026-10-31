package me.romanovsky.anyplayer.images

import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import org.junit.Assert.*
import org.junit.Test

class ImageCanvasTest {
    @Test fun `video stop frame keeps the viewport footprint instead of shrinking to decoded pixels`() {
        SwingUtilities.invokeAndWait {
            val frame = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply { color = Color.RED; fillRect(0, 0, 320, 180); dispose() }
            }
            val view = ImageCanvas().apply { image = frame; videoMode = true; setSize(1280, 720) }
            val output = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB)
            val graphics = output.createGraphics()
            try { view.paint(graphics) } finally { graphics.dispose() }
            assertEquals(Color.RED.rgb, output.getRGB(10, 10))
            assertEquals(Color.RED.rgb, output.getRGB(1260, 700))
            view.videoMode = false
            val second = output.createGraphics()
            try { view.paint(second) } finally { second.dispose() }
            assertNotEquals(Color.RED.rgb, output.getRGB(10, 10))
        }
    }
}
