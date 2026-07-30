package com.github.kr328.clash.common.compat

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

val Application.currentProcessName: String
    get() = Application.getProcessName()

fun Drawable.foreground(): Drawable {
    if (this is AdaptiveIconDrawable && this.background == null) {
        return this.foreground
    }
    return this
}
