package com.adnanearrassen.ytarchiver.core.common

import java.util.Locale
import kotlin.math.abs

/** Human-readable formatting helpers used across the UI. */
object Formatters {

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "${value} B"
        else String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    fun speed(bytesPerSec: Long): String =
        if (bytesPerSec <= 0) "—" else "${bytes(bytesPerSec)}/s"

    /** hh:mm:ss / mm:ss for a duration in seconds. */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "--:--"
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "3m 12s left" style ETA. */
    fun eta(seconds: Long): String {
        if (seconds <= 0) return "—"
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m <= 0 -> "${s}s left"
            m < 60 -> "${m}m ${s}s left"
            else -> "${m / 60}h ${m % 60}m left"
        }
    }

    fun count(value: Long): String = when {
        abs(value) >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1e9)
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
        abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
        else -> value.toString()
    }
}
