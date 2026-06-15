package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.LiveNotification
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.Locale
import java.util.concurrent.TimeUnit

class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
    private var profileName: String = StatusProvider.currentProfile ?: ""
    private var node: String? = null
    private var tick = 0

    private fun update() {
        val now = Clash.queryTrafficNow()
        val total = Clash.queryTrafficTotal()

        val uploading = now.trafficUpload()
        val downloading = now.trafficDownload()
        val uploaded = total.trafficUpload()
        val downloaded = total.trafficDownload()

        // Querying the selected node deserializes the whole proxy list, so refresh it less
        // often than the per-second traffic counters.
        if (tick % NODE_REFRESH_INTERVAL == 0) {
            node = queryCurrentNode()
        }
        tick++

        val activeNode = node?.takeIf { it.isNotBlank() }
        val title = activeNode
            ?: profileName.takeIf { it.isNotBlank() }
            ?: service.getString(R.string.notification_connected)

        val content = service.getString(
            R.string.clash_notification_content,
            "$uploading/s", "$downloading/s"
        )

        // Show the profile as a subtitle only when the node is already used as the title,
        // otherwise fall back to the lifetime traffic totals.
        val subText = if (activeNode != null && profileName.isNotBlank()) {
            profileName
        } else {
            service.getString(R.string.clash_notification_content, uploaded, downloaded)
        }

        val notification = LiveNotification.buildConnected(
            context = service,
            title = title,
            contentText = content,
            shortText = "↓" + compactSpeed(downloading),
            subText = subText,
        )

        LiveNotification.notify(service, notification)
    }

    private fun queryCurrentNode(): String? {
        return try {
            Clash.queryGroupNames(true).firstNotNullOfOrNull { name ->
                Clash.queryGroup(name, ProxySort.Default).now.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun run() = coroutineScope {
        var shouldUpdate = service.getSystemService<PowerManager>()?.isInteractive ?: true

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        val profileLoaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (true) {
            select<Unit> {
                screenToggle.onReceive {
                    when (it.action) {
                        Intent.ACTION_SCREEN_ON ->
                            shouldUpdate = true
                        Intent.ACTION_SCREEN_OFF ->
                            shouldUpdate = false
                    }
                }
                profileLoaded.onReceive {
                    profileName = StatusProvider.currentProfile ?: ""
                    // Force a node refresh on the next tick for the new profile.
                    tick = 0
                    node = null
                }
                if (shouldUpdate) {
                    ticker.onReceive {
                        update()
                    }
                }
            }
        }
    }

    companion object {
        private const val NODE_REFRESH_INTERVAL = 3

        /**
         * Compacts a formatted traffic string (e.g. "1.23 MiB") into a chip-friendly form
         * (e.g. "1.2M") so it fits the ~7 character status bar chip budget.
         */
        private fun compactSpeed(value: String): String {
            val parts = value.split(' ')
            if (parts.size < 2) return value

            val number = parts[0]
            val unit = when (parts[1].firstOrNull()?.uppercaseChar()) {
                'G' -> "G"
                'M' -> "M"
                'K' -> "K"
                else -> "B"
            }

            val trimmed = number.toFloatOrNull()?.let { f ->
                if (f >= 10f) f.toInt().toString() else String.format(Locale.US, "%.1f", f)
            } ?: number

            return "$trimmed$unit"
        }
    }
}
