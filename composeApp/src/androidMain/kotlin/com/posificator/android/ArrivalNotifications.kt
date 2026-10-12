package com.posificator.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.posificator.model.Place
import com.posificator.model.arrivalMessage
import com.posificator.model.arrivalTitle
import com.posificator.model.normalizedLink

object ArrivalNotifications {
    private const val CHANNEL_ID = "arrivals"
    private const val ALARM_CHANNEL_ID = "arrival_alarms"
    private val VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800)

    private fun alarmSound(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Settings.System.DEFAULT_NOTIFICATION_URI

    private val alarmAudio: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun notificationId(place: Place) = place.id.hashCode()

    fun show(context: Context, place: Place) {
        if (!context.notificationsAllowed()) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Arrivals", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when you arrive at a saved place"
                },
            )
            // A channel's sound can't change after creation, so alarms get their own channel.
            manager.createNotificationChannel(
                NotificationChannel(ALARM_CHANNEL_ID, "Arrival alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Places set to ring an alarm when you arrive"
                    setSound(alarmSound(), alarmAudio)
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                    setBypassDnd(true)
                },
            )
            Notification.Builder(context, if (place.alarm) ALARM_CHANNEL_ID else CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).apply {
                if (place.alarm) {
                    setSound(alarmSound(), alarmAudio)
                    setVibrate(VIBRATION)
                } else {
                    setDefaults(Notification.DEFAULT_ALL)
                }
            }
        }

        // Tapping opens the place's link if it has one, otherwise the app.
        val tapIntent = place.normalizedLink()
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val onTap = PendingIntent.getActivity(
            context,
            notificationId(place),
            tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(place.arrivalTitle())
            .setContentText(place.arrivalMessage())
            .setStyle(Notification.BigTextStyle().bigText(place.arrivalMessage()))
            .setContentIntent(onTap)
            .setAutoCancel(true)

        if (place.alarm) {
            val stop = PendingIntent.getBroadcast(
                context,
                notificationId(place),
                Intent(context, StopAlarmReceiver::class.java).putExtra(StopAlarmReceiver.EXTRA_ID, notificationId(place)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_lock_idle_alarm), "Stop alarm", stop).build())
        }

        val notification = builder.build()
        // Insistent: the sound repeats until the notification is tapped, swiped away or stopped.
        if (place.alarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            manager.notify(notificationId(place), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked between the check and the post.
        }
    }
}

/** Handles the notification's "Stop alarm" button by removing the notification, which silences it. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    companion object {
        const val EXTRA_ID = "notificationId"
    }
}
