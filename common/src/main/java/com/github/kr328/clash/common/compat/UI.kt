package com.github.kr328.clash.common.compat

import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

var Window.isSystemBarsTranslucentCompat: Boolean
    get() {
        throw UnsupportedOperationException("set value only")
    }
    set(value) {
        WindowCompat.setDecorFitsSystemWindows(this, !value)

        attributes.layoutInDisplayCutoutMode =
            if (value) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
    }

var Window.isLightStatusBarsCompat: Boolean
    get() {
        throw UnsupportedOperationException("set value only")
    }
    set(value) {
        WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars = value
    }

var Window.isLightNavigationBarCompat: Boolean
    get() {
        throw UnsupportedOperationException("set value only")
    }
    set(value) {
        WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = value
    }

var Window.isAllowForceDarkCompat: Boolean
    get() {
        return decorView.isForceDarkAllowed
    }
    set(value) {
        decorView.isForceDarkAllowed = value
    }
