package com.posificator.model

import kotlinx.serialization.Serializable

/** A saved location the user wants to be notified about when they arrive. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val enabled: Boolean = true,
    /** Optional first and last day the alarm works, as yyyyMMdd (both inclusive). */
    val startDate: Int? = null,
    val endDate: Int? = null,
    /** Optional daily window in minutes after midnight (both inclusive); may wrap past midnight. */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
) {
    companion object {
        /** Both platforms are unreliable below ~100 m, so the slider starts there. */
        const val MIN_RADIUS_METERS = 100.0
        const val MAX_RADIUS_METERS = 5_000.0
        const val DEFAULT_RADIUS_METERS = 200.0
    }
}

/** The device's local date and time, as the schedule checks need it. */
data class LocalNow(val date: Int, val minuteOfDay: Int)

expect fun currentLocalNow(): LocalNow

/** Whether an arrival right now should notify, given the place's date range and daily hours. */
fun Place.isActiveAt(now: LocalNow): Boolean {
    if (startDate != null && now.date < startDate) return false
    if (endDate != null && now.date > endDate) return false
    if (startMinute != null && endMinute != null && startMinute != endMinute) {
        val m = now.minuteOfDay
        val inWindow = if (startMinute < endMinute) {
            m in startMinute..endMinute
        } else {
            m >= startMinute || m <= endMinute
        }
        if (!inWindow) return false
    }
    return true
}

/** True once the date range has ended, so there is no point watching the place any more. */
fun Place.isExpiredAt(now: LocalNow): Boolean = endDate != null && now.date > endDate

fun Place.arrivalTitle(): String = "You've arrived"

fun Place.arrivalMessage(): String = "You're now at $name."
