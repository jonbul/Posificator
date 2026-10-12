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
    /** Optional days of the week the alarm works, 1 = Monday … 7 = Sunday. Null means every day. */
    val weekdays: List<Int>? = null,
    /** Optional text shown in the notification instead of the default one. */
    val message: String? = null,
    /** Optional web address opened when the notification is tapped. */
    val link: String? = null,
) {
    companion object {
        /** Both platforms are unreliable below ~100 m, so the slider starts there. */
        const val MIN_RADIUS_METERS = 100.0
        const val MAX_RADIUS_METERS = 50_000.0
        const val DEFAULT_RADIUS_METERS = 200.0
    }
}

/** The device's local date and time, as the schedule checks need it. */
data class LocalNow(val date: Int, val minuteOfDay: Int)

expect fun currentLocalNow(): LocalNow

/** Epoch millis for a local date and time in the device's time zone. */
expect fun epochMillisAt(local: LocalNow): Long

/** Whether an arrival right now should notify, given the place's date range and daily hours. */
fun Place.isActiveAt(now: LocalNow): Boolean {
    if (startDate != null && now.date < startDate) return false
    if (endDate != null && now.date > endDate) return false
    var day = now.date
    if (startMinute != null && endMinute != null && startMinute != endMinute) {
        val m = now.minuteOfDay
        val inWindow = if (startMinute < endMinute) {
            m in startMinute..endMinute
        } else {
            m >= startMinute || m <= endMinute
        }
        if (!inWindow) return false
        // The after-midnight part of an overnight window belongs to the day it started on.
        if (startMinute > endMinute && m <= endMinute) day = Dates.addDays(day, -1)
    }
    if (weekdays != null && Dates.dayOfWeek(day) !in weekdays) return false
    return true
}

/**
 * The next moment after [now] when the place becomes active, or null if it never will.
 * Only meaningful when the place is not active at [now].
 */
fun Place.nextActivation(now: LocalNow): LocalNow? {
    val hasHours = startMinute != null && endMinute != null && startMinute != endMinute
    val today = Dates.toEpochDays(now.date)
    for (offset in 0..366) {
        val day = Dates.fromEpochDays(today + offset)
        if (endDate != null && day > endDate) return null
        if (startDate != null && day < startDate) continue
        if (weekdays != null && Dates.dayOfWeek(day) !in weekdays) continue
        val minute = if (hasHours) startMinute!! else 0
        if (offset == 0 && minute <= now.minuteOfDay) continue
        return LocalNow(day, minute)
    }
    return null
}

/** True once the date range has ended, so there is no point watching the place any more. */
fun Place.isExpiredAt(now: LocalNow): Boolean = endDate != null && now.date > endDate

private val Place.customMessage get() = message?.trim()?.takeIf { it.isNotEmpty() }

fun Place.arrivalTitle(): String = if (customMessage != null) name else "You've arrived"

fun Place.arrivalMessage(): String = customMessage ?: "You're now at $name."

/** The link with a scheme added if the user typed a bare address like "example.com". */
fun Place.normalizedLink(): String? = normalizeLink(link)

fun normalizeLink(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
}
