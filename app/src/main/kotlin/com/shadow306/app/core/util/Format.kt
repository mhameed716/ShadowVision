package com.shadow306.app.core.util

import java.util.Locale
import kotlin.math.abs

/**
 * Human readable formatting helpers.
 *
 * Everything is formatted with the *device* locale on purpose: an Arabic UI shows
 * Arabic-Indic digits and an English UI shows Latin digits.
 */
object Format {

    private const val KIB = 1024.0
    private const val MIB = KIB * 1024.0
    private const val GIB = MIB * 1024.0

    /** `1.2 GB`, `512 MB`, `812 KB`, `16 B`. */
    fun bytes(value: Long): String {
        val v = value.toDouble()
        return when {
            abs(v) >= GIB -> "%.2f GB".formatLocale(v / GIB)
            abs(v) >= MIB -> "%.1f MB".formatLocale(v / MIB)
            abs(v) >= KIB -> "%.0f KB".formatLocale(v / KIB)
            else -> "%d B".formatLocale(value)
        }
    }

    /** Download rate, e.g. `4.2 MB/s`. */
    fun speed(bytesPerSecond: Long): String = when {
        bytesPerSecond <= 0 -> "—"
        else -> "%s/s".formatLocale(bytes(bytesPerSecond))
    }

    /** Coarse duration: `45s`, `3m 20s`, `1h 04m`. */
    fun duration(millis: Long): String {
        if (millis < 0) return "—"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%dh %02dm".formatLocale(hours, minutes)
            minutes > 0 -> "%dm %02ds".formatLocale(minutes, seconds)
            else -> "%ds".formatLocale(seconds)
        }
    }

    /** Clock style duration used by the video screens: `0:05`, `1:00`. */
    fun clock(millis: Long): String {
        val totalSeconds = (millis + 500) / 1000
        return "%d:%02d".formatLocale(totalSeconds / 60, totalSeconds % 60)
    }

    /** Percentage in 0..100, safe when [total] is unknown or zero. */
    fun percent(part: Long, total: Long): Int = when {
        total <= 0L -> 0
        part <= 0L -> 0
        part >= total -> 100
        else -> ((part * 100.0) / total).toInt().coerceIn(0, 100)
    }

    private fun String.formatLocale(vararg args: Any?): String =
        String.format(Locale.getDefault(), this, *args)
}
