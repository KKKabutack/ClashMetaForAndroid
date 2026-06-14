package com.github.kr328.clash.common.compat

import android.app.PendingIntent

fun pendingIntentFlags(flags: Int, mutable: Boolean = false): Int {
    return if (mutable) {
        flags or PendingIntent.FLAG_MUTABLE
    } else {
        flags or PendingIntent.FLAG_IMMUTABLE
    }
}
