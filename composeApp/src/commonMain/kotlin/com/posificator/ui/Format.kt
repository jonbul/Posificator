package com.posificator.ui

import kotlin.math.abs
import kotlin.math.roundToLong

internal fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToLong()} m" else "${fixed(meters / 1000, 1)} km"

internal fun formatCoordinate(value: Double): String = fixed(value, 5)

/** Fixed-decimal formatting without String.format, which isn't available in common code. */
private fun fixed(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return "$sign$whole.$fraction"
}
