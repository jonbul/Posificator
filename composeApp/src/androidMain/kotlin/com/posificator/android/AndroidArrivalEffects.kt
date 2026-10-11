package com.posificator.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.posificator.arrival.ArrivalEffects
import com.posificator.arrival.ArrivalEngine
import com.posificator.model.Place

class AndroidArrivalEffects(context: Context) : ArrivalEffects {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    override fun notifyArrival(place: Place) = ArrivalNotifications.show(appContext, place)

    override fun scheduleWindowStart(place: Place, epochMillis: Long) {
        // Inexact is fine here and needs no special alarm permission; it may fire a few minutes late.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, windowStartIntent(place.id))
    }

    override fun cancelWindowStart(placeId: String) {
        alarms.cancel(windowStartIntent(placeId))
    }

    private fun windowStartIntent(placeId: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, WindowStartReceiver::class.java)
                .setData(Uri.parse("posificator://window/$placeId"))
                .putExtra(WindowStartReceiver.EXTRA_PLACE_ID, placeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

fun arrivalEngine(context: Context) =
    ArrivalEngine(SharedPrefsStorage(context), placeRepository(context), AndroidArrivalEffects(context))
