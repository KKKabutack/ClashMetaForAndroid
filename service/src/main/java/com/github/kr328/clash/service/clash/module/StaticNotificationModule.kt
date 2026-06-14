package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.LiveNotification
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel

class StaticNotificationModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val loaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        while (true) {
            loaded.receive()

            val profileName = StatusProvider.currentProfile
                ?: service.getString(R.string.notification_connected)

            val notification = LiveNotification.buildConnected(
                context = service,
                title = profileName,
                contentText = service.getString(R.string.running),
                shortText = null,
                subText = null,
            )

            service.startForegroundCompat(LiveNotification.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = LiveNotification.CHANNEL_ID

        fun createNotificationChannel(service: Service) {
            LiveNotification.createChannel(service)
        }

        fun notifyLoadingNotification(service: Service) {
            service.startForegroundCompat(
                LiveNotification.NOTIFICATION_ID,
                LiveNotification.buildConnecting(service)
            )
        }
    }
}
