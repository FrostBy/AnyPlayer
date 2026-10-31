package dev.anyplayer.analysis

import org.junit.Assert.*
import org.junit.Test

class WaveformViewTest {
    @Test fun `buffered waveform keeps source coordinates while the viewport slides`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val image = java.awt.image.BufferedImage(300, 80, java.awt.image.BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply {
                color = java.awt.Color.RED; fillRect(0, 0, 100, 80)
                color = java.awt.Color.GREEN; fillRect(100, 0, 100, 80)
                color = java.awt.Color.BLUE; fillRect(200, 0, 100, 80)
                dispose()
            }
            val view = WaveformView {}.apply {
                setSize(120, 80)
                this.image = image
                imageStartMicros = 0
                imageEndMicros = 3_000_000
                viewStartMicros = 1_000_000
                viewEndMicros = 2_000_000
            }
            fun paint(): java.awt.image.BufferedImage = java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB).also {
                it.createGraphics().apply { view.paint(this); dispose() }
            }
            assertEquals(java.awt.Color.GREEN.rgb, paint().getRGB(60, 10))
            view.viewStartMicros += 100_000
            view.viewEndMicros += 100_000
            assertSame(image, view.image)
            val next = paint()
            assertEquals(java.awt.Color.GREEN.rgb, next.getRGB(60, 10))
            assertEquals(java.awt.Color.BLUE.rgb, next.getRGB(115, 10))
        }
    }

    @Test fun `seek coordinates use the visible range and clamp outside the image`() {
        assertEquals(15_000_000L, WaveformView.timeAtX(50, 100, 10_000_000, 20_000_000))
        assertEquals(10_000_000L, WaveformView.timeAtX(-10, 100, 10_000_000, 20_000_000))
        assertEquals(20_000_000L, WaveformView.timeAtX(200, 100, 10_000_000, 20_000_000))
        assertEquals(50, WaveformView.xAtTime(15_000_000, 100, 10_000_000, 20_000_000))
    }

    @Test fun `zoom preserves its anchor and pan stays within the source`() {
        assertEquals(25_000_000L to 75_000_000L, WaveformView.zoomWindow(0, 100_000_000, .5, .5, 100_000_000))
        assertEquals(0L to 50_000_000L, WaveformView.panWindow(25_000_000, 75_000_000, -100_000_000, 100_000_000))
        assertEquals(50_000_000L to 100_000_000L, WaveformView.panWindow(25_000_000, 75_000_000, 100_000_000, 100_000_000))
        assertEquals(0L to 30_000L, WaveformView.zoomWindow(0, 30_000, .5, .5, 30_000))
    }

    @Test fun `time ruler becomes more precise as the visible range narrows`() {
        val ticks = WaveformView.computeTicks(0, 1_000_000, 800, 80)
        assertEquals("00:00.0", ticks.first().label)
        assertTrue(ticks.size <= 11)
        assertTrue(WaveformView.computeTicks(1, 1, 800, 80).isEmpty())
    }
}
