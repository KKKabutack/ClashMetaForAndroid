package com.github.kr328.clash.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.ui.Insets

fun View.setOnInsertsChangedListener(adaptLandscape: Boolean = true, listener: (Insets) -> Unit) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insetsCompat ->
        // System bars + display cutout so content stays clear of the notch/punch-hole in every
        // orientation (in landscape the cutout sits on the side where system bar insets are 0).
        val insets = insetsCompat.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )

        val rInsets = if (ViewCompat.getLayoutDirection(v) == ViewCompat.LAYOUT_DIRECTION_LTR) {
            Insets(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom,
            )
        } else {
            Insets(
                insets.right,
                insets.top,
                insets.left,
                insets.bottom,
            )
        }

        listener(if (adaptLandscape) rInsets.landscape(v.context) else rInsets)

        // Do not consume insets for descendants; return as-is so inner views can also observe if needed.
        insetsCompat
    }

    ViewCompat.requestApplyInsets(this)
}
