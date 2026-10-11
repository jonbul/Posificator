package com.posificator.model

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

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
