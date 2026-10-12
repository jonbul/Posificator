package com.posificator.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.posificator.model.Place
import com.posificator.model.arrivalMessage
import com.posificator.model.arrivalTitle
import com.posificator.model.normalizedLink

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

        // Tapping opens the place's link if it has one, otherwise the app.
        val tapIntent = place.normalizedLink()
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val onTap = PendingIntent.getActivity(
            context,
            place.id.hashCode(),
            tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(place.arrivalTitle())
            .setContentText(place.arrivalMessage())
            .setStyle(Notification.BigTextStyle().bigText(place.arrivalMessage()))
            .setContentIntent(onTap)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(place.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked between the check and the post.
        }
    }
}
