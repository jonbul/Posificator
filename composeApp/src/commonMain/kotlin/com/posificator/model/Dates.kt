package com.posificator.model

/** Converts between yyyyMMdd ints and days since 1970-01-01, without a date library. */
object Dates {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun of(year: Int, month: Int, day: Int): Int = year * 10_000 + month * 100 + day

    fun year(date: Int) = date / 10_000
    fun month(date: Int) = date / 100 % 100
    fun day(date: Int) = date % 100

    /** Material date pickers report the chosen day as UTC midnight in epoch millis. */
    fun fromUtcMillis(millis: Long): Int = fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))

    fun toUtcMillis(date: Int): Long = toEpochDays(date) * MILLIS_PER_DAY

    // Howard Hinnant's civil-from-days / days-from-civil algorithms.
    fun fromEpochDays(epochDays: Long): Int {
        val z = epochDays + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return of(y, m, d)
    }

    fun toEpochDays(date: Int): Long {
        val m = month(date)
        val y = year(date).toLong() - if (m <= 2) 1 else 0
        val era = y.floorDiv(400L)
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + day(date) - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    fun addDays(date: Int, days: Int): Int = fromEpochDays(toEpochDays(date) + days)

    /** 1 = Monday … 7 = Sunday. 1970-01-01 was a Thursday. */
    fun dayOfWeek(date: Int): Int = (toEpochDays(date) + 3).mod(7) + 1

    val WEEKDAY_SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun format(date: Int): String =
        "${year(date)}-${month(date).toString().padStart(2, '0')}-${day(date).toString().padStart(2, '0')}"

    fun formatMinute(minuteOfDay: Int): String =
        "${(minuteOfDay / 60).toString().padStart(2, '0')}:${(minuteOfDay % 60).toString().padStart(2, '0')}"
}
