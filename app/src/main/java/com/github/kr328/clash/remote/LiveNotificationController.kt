package com.github.kr328.clash.remote

import android.app.Application
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.LiveNotification
import java.util.UUID

/**
 * Keeps the VPN control "Live Update" notification present from the moment the app launches.
 *
 * While the tunnel is running the foreground service owns the notification (rich connected card
 * with live speed + node). When the tunnel is stopped this controller re-posts the lightweight
 * "Disconnected" control card so the user can start the VPN again straight from the shade.
 */
object LiveNotificationController : Broadcasts.Observer {
    private lateinit var application: Application
    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        initialized = true

        this.application = application

        LiveNotification.createChannel(application)
        Remote.broadcasts.addObserver(this)

        refresh()
    }

    /** Posts the disconnected control card when the tunnel is not currently running. */
    fun refresh() {
        if (!initialized) return

        val running = try {
            StatusClient(application).currentProfile() != null
        } catch (e: Exception) {
            Log.w("Query clash status for live notification: $e", e)
            Remote.broadcasts.clashRunning
        }

        if (!running) {
            LiveNotification.showDisconnected(application)
        }
    }

    override fun onStarted() {
        // The foreground service replaces the card with the connected live update.
    }

    override fun onStopped(cause: String?) {
        LiveNotification.showDisconnected(application)
    }

    override fun onServiceRecreated() {
        refresh()
    }

    override fun onProfileChanged() {}
    override fun onProfileUpdateCompleted(uuid: UUID?) {}
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {}
    override fun onProfileLoaded() {}
}
