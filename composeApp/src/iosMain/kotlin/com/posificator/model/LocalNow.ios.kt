package com.posificator.model

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.timeIntervalSince1970

actual fun currentLocalNow(): LocalNow {
    val c = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate(),
    )
    return LocalNow(
        date = Dates.of(c.year.toInt(), c.month.toInt(), c.day.toInt()),
        minuteOfDay = (c.hour * 60 + c.minute).toInt(),
    )
}

actual fun epochMillisAt(local: LocalNow): Long {
    val components = NSDateComponents().apply {
        year = Dates.year(local.date).toLong()
        month = Dates.month(local.date).toLong()
        day = Dates.day(local.date).toLong()
        hour = (local.minuteOfDay / 60).toLong()
        minute = (local.minuteOfDay % 60).toLong()
    }
    val date = NSCalendar.currentCalendar.dateFromComponents(components) ?: return 0L
    return (date.timeIntervalSince1970 * 1000).toLong()
}
