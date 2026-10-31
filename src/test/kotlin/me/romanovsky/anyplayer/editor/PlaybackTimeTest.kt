package me.romanovsky.anyplayer.editor

import org.junit.Assert.*
import org.junit.Test

class PlaybackTimeTest {
    @Test fun `go to time accepts source times and rejects invalid or out of range input`() {
        assertEquals(90.0, MediaEditor.parseTime("1:30", 4000.0)!!, .001)
        assertEquals(3723.25, MediaEditor.parseTime("1:02:03.250", 4000.0)!!, .001)
        assertEquals(12.5, MediaEditor.parseTime("12.5", 4000.0)!!, .001)
        for (value in listOf("-1", "NaN", "Infinity", "1:60", "99:99", "4001", "1.1234", "text")) assertNull(value, MediaEditor.parseTime(value, 4000.0))
    }
}
