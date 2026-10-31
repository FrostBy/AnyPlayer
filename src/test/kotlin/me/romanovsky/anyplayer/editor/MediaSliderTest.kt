package me.romanovsky.anyplayer.editor

import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import org.junit.Assert.*
import org.junit.Test

class MediaSliderTest {
    @Test fun `hover reports pointer time without changing playback position`() {
        SwingUtilities.invokeAndWait {
            val slider = MediaSlider(0, 1_000_000, 100_000)
            slider.setSize(400, 30)
            val graphics = java.awt.image.BufferedImage(400, 30, java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics()
            try { slider.paint(graphics) } finally { graphics.dispose() }
            var preview = 0
            var exited = false
            slider.onPreview = { value, _ -> preview = value }
            slider.onPreviewExit = { exited = true }
            slider.dispatchEvent(MouseEvent(slider, MouseEvent.MOUSE_MOVED, 0, 0, 300, 15, 0, false))
            assertTrue(preview in 700_000..800_000)
            assertEquals(100_000, slider.value)
            assertFalse(slider.valueIsAdjusting)
            slider.dispatchEvent(MouseEvent(slider, MouseEvent.MOUSE_EXITED, 0, 0, 410, 15, 0, false))
            assertTrue(exited)
        }
    }

    @Test fun `track clicks jump to the pointer and dragging commits only on release`() {
        SwingUtilities.invokeAndWait {
            val slider = MediaSlider(0, 1_000_000, 0)
            slider.setSize(400, 30)
            slider.doLayout()
            val image = java.awt.image.BufferedImage(400, 30, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try { slider.paint(graphics) } finally { graphics.dispose() }
            val committed = mutableListOf<Int>()
            slider.addChangeListener { if (!slider.valueIsAdjusting) committed.add(slider.value) }
            fun mouse(id: Int, x: Int, button: Int) = slider.dispatchEvent(MouseEvent(slider, id, System.currentTimeMillis(), 0, x, 15, 1, false, button))
            mouse(MouseEvent.MOUSE_PRESSED, 300, MouseEvent.BUTTON1)
            assertTrue(slider.valueIsAdjusting)
            assertTrue("Click value was ${slider.value}", slider.value in 700_000..800_000)
            mouse(MouseEvent.MOUSE_DRAGGED, 200, MouseEvent.NOBUTTON)
            assertTrue(slider.value in 450_000..550_000)
            assertTrue(committed.isEmpty())
            mouse(MouseEvent.MOUSE_RELEASED, 200, MouseEvent.BUTTON1)
            assertFalse(slider.valueIsAdjusting)
            assertEquals(1, committed.size)
            assertTrue(committed.single() in 450_000..550_000)
        }
    }
}
