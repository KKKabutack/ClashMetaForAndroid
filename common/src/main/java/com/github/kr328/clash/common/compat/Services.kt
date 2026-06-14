package com.github.kr328.clash.common.compat

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo

fun Context.startForegroundServiceCompat(intent: Intent) {
    startForegroundService(intent)
}

fun Service.startForegroundCompat(id: Int, notification: Notification) {
    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
}
