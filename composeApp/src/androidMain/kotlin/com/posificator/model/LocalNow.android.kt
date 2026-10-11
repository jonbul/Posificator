package com.posificator.model

import java.util.Calendar

actual fun currentLocalNow(): LocalNow {
    val c = Calendar.getInstance()
    return LocalNow(
        date = Dates.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)),
        minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
    )
}

actual fun epochMillisAt(local: LocalNow): Long =
    Calendar.getInstance().apply {
        clear()
        set(Dates.year(local.date), Dates.month(local.date) - 1, Dates.day(local.date), local.minuteOfDay / 60, local.minuteOfDay % 60)
    }.timeInMillis
