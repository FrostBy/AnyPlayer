package dev.anyplayer.editor

import java.awt.event.MouseEvent
import java.awt.Cursor
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI

class MediaSlider(minimum: Int, maximum: Int, value: Int) : JSlider(minimum, maximum, value) {
    var onPreview: ((Int, Int) -> Unit)? = null
    var onPreviewExit: (() -> Unit)? = null
    init { cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
    private fun valueAt(x: Int): Int = (ui as? BasicSliderUI)?.valueForXPosition(x)
        ?: (minimum + x.toDouble() / width.coerceAtLeast(1) * (maximum - minimum)).toInt().coerceIn(minimum, maximum)
    private fun moveTo(event: MouseEvent) {
        value = valueAt(event.x)
        onPreview?.invoke(value, event.x)
    }

    override fun processMouseEvent(event: MouseEvent) {
        if (event.id == MouseEvent.MOUSE_EXITED && !valueIsAdjusting) onPreviewExit?.invoke()
        if (isEnabled && event.button == MouseEvent.BUTTON1) {
            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> {
                    requestFocusInWindow()
                    valueIsAdjusting = true
                    moveTo(event)
                    event.consume()
                    return
                }
                MouseEvent.MOUSE_RELEASED -> if (valueIsAdjusting) {
                    moveTo(event)
                    valueIsAdjusting = false
                    if (!contains(event.point)) onPreviewExit?.invoke()
                    event.consume()
                    return
                }
            }
        }
        super.processMouseEvent(event)
    }

    override fun processMouseMotionEvent(event: MouseEvent) {
        if (isEnabled && valueIsAdjusting && event.id == MouseEvent.MOUSE_DRAGGED) {
            moveTo(event)
            event.consume()
        } else {
            if (isEnabled && event.id == MouseEvent.MOUSE_MOVED) onPreview?.invoke(valueAt(event.x), event.x)
            super.processMouseMotionEvent(event)
        }
    }
}
