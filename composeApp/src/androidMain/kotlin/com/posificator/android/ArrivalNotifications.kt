package com.posificator.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.posificator.model.Place
import com.posificator.model.arrivalMessage
import com.posificator.model.arrivalTitle

object ArrivalNotifications {
    private const val CHANNEL_ID = "arrivals"

    fun show(context: Context, place: Place) {
        if (!context.notificationsAllowed()) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Arrivals", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when you arrive at a saved place"
                },
            )
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(place.arrivalTitle())
            .setContentText(place.arrivalMessage())
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(place.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked between the check and the post.
        }
    }
}
