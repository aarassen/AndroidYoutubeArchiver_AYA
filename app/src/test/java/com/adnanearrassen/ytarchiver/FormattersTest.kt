package com.adnanearrassen.ytarchiver

import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.core.common.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun bytes_formatsHumanReadable() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.0 KB", Formatters.bytes(1024))
        assertEquals("1.5 MB", Formatters.bytes(1_572_864))
    }

    @Test
    fun duration_switchesToHoursWhenNeeded() {
        assertEquals("0:45", Formatters.duration(45))
        assertEquals("2:05", Formatters.duration(125))
        assertEquals("1:01:05", Formatters.duration(3665))
    }

    @Test
    fun urlUtils_detectsYouTubeLinks() {
        assertTrue(UrlUtils.isYouTubeUrl("check this https://youtu.be/dQw4w9WgXcQ out"))
        assertTrue(UrlUtils.isPlaylistUrl("https://www.youtube.com/playlist?list=PL123"))
        assertEquals("https://youtu.be/abc", UrlUtils.firstUrlIn("shared: https://youtu.be/abc."))
    }
}
