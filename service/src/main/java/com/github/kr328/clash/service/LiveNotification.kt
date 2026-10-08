package com.github.kr328.clash.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents

/**
 * Builds and posts the VPN status notification.
 *
 * On Android 16 QPR1 (36.1)+ this becomes a promoted ongoing "Live Update" (with a status bar
 * chip). On base Android 16 (36.0) the promotion request is a graceful no-op and the exact same
 * notification is shown as a regular ongoing foreground notification. [NotificationCompat] handles
 * this version gating for us, so no manual SDK checks are required.
 *
 * The single notification id is reused across two states so they replace each other:
 *  - [buildConnecting] / [buildConnected]: owned by the foreground service while the tunnel is up
 *    (node + live speed + a Disconnect action).
 */
object LiveNotification {
    const val CHANNEL_ID = "clash_status_channel"

    val NOTIFICATION_ID: Int = R.id.nf_clash_status

    private const val REQUEST_CONTENT = 0xC1A50001.toInt()
    private const val REQUEST_DISCONNECT = 0xC1A50003.toInt()

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID,
                // Promoted ongoing live updates require importance above IMPORTANCE_MIN.
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName(context.getString(R.string.clash_service_status_channel))
                .setShowBadge(false)
                .build()
        )
    }

    fun buildConnecting(context: Context): Notification {
        return baseBuilder(context)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(context.getString(R.string.notification_connecting))
            .setShortCriticalText(context.getString(R.string.notification_short_connecting))
            .setProgress(0, 0, true)
            .addAction(disconnectAction(context))
            .build()
    }

    fun buildConnected(
        context: Context,
        title: CharSequence,
        contentText: CharSequence?,
        shortText: String?,
        subText: CharSequence?,
    ): Notification {
        return baseBuilder(context)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(title)
            .apply {
                if (contentText != null) setContentText(contentText)
                if (subText != null) setSubText(subText)
                if (shortText != null) setShortCriticalText(shortText)
            }
            .addAction(disconnectAction(context))
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, notification: Notification) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(context.getColorCompat(R.color.color_clash))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent()
            .setComponent(Components.MAIN_ACTIVITY)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

        return PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            intent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    private fun disconnectAction(context: Context): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_disconnect,
            context.getString(R.string.notification_action_disconnect),
            controlIntent(context, Intents.ACTION_STOP_CLASH, REQUEST_DISCONNECT)
        ).build()
    }

    private fun controlIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action)
            .setComponent(Components.EXTERNAL_CONTROL_ACTIVITY)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }
}
