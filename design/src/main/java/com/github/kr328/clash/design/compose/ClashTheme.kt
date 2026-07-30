package com.github.kr328.clash.design.compose

import androidx.annotation.AttrRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor

/**
 * Compose counterpart of the XML Amber Gold Material 3 scheme.
 *
 * Values come from the activity's resolved theme, so Force Light and Force Dark
 * preferences produce the same scheme in Compose and View-based screens.
 */
@Composable
fun ClashTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun color(@AttrRes attr: Int) = Color(context.resolveThemedColor(attr))

    val scheme = if (context.resolveThemedBoolean(R.attr.isDarkTheme)) {
        darkColorScheme(
            primary = color(androidx.appcompat.R.attr.colorPrimary),
            onPrimary = color(com.google.android.material.R.attr.colorOnPrimary),
            primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer),
            onPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer),
            secondary = color(com.google.android.material.R.attr.colorSecondary),
            onSecondary = color(com.google.android.material.R.attr.colorOnSecondary),
            secondaryContainer = color(com.google.android.material.R.attr.colorSecondaryContainer),
            onSecondaryContainer = color(com.google.android.material.R.attr.colorOnSecondaryContainer),
            tertiary = color(com.google.android.material.R.attr.colorTertiary),
            onTertiary = color(com.google.android.material.R.attr.colorOnTertiary),
            tertiaryContainer = color(com.google.android.material.R.attr.colorTertiaryContainer),
            onTertiaryContainer = color(com.google.android.material.R.attr.colorOnTertiaryContainer),
            background = color(android.R.attr.colorBackground),
            onBackground = color(com.google.android.material.R.attr.colorOnBackground),
            surface = color(com.google.android.material.R.attr.colorSurface),
            onSurface = color(com.google.android.material.R.attr.colorOnSurface),
            surfaceVariant = color(com.google.android.material.R.attr.colorSurfaceVariant),
            onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
            surfaceContainerLowest = color(com.google.android.material.R.attr.colorSurfaceContainerLowest),
            surfaceContainerLow = color(com.google.android.material.R.attr.colorSurfaceContainerLow),
            surfaceContainer = color(com.google.android.material.R.attr.colorSurfaceContainer),
            surfaceContainerHigh = color(com.google.android.material.R.attr.colorSurfaceContainerHigh),
            surfaceContainerHighest = color(com.google.android.material.R.attr.colorSurfaceContainerHighest),
            outline = color(com.google.android.material.R.attr.colorOutline),
            outlineVariant = color(com.google.android.material.R.attr.colorOutlineVariant),
            error = color(android.R.attr.colorError),
            onError = color(com.google.android.material.R.attr.colorOnError),
            errorContainer = color(com.google.android.material.R.attr.colorErrorContainer),
            onErrorContainer = color(com.google.android.material.R.attr.colorOnErrorContainer),
        )
    } else {
        lightColorScheme(
            primary = color(androidx.appcompat.R.attr.colorPrimary),
            onPrimary = color(com.google.android.material.R.attr.colorOnPrimary),
            primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer),
            onPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer),
            secondary = color(com.google.android.material.R.attr.colorSecondary),
            onSecondary = color(com.google.android.material.R.attr.colorOnSecondary),
            secondaryContainer = color(com.google.android.material.R.attr.colorSecondaryContainer),
            onSecondaryContainer = color(com.google.android.material.R.attr.colorOnSecondaryContainer),
            tertiary = color(com.google.android.material.R.attr.colorTertiary),
            onTertiary = color(com.google.android.material.R.attr.colorOnTertiary),
            tertiaryContainer = color(com.google.android.material.R.attr.colorTertiaryContainer),
            onTertiaryContainer = color(com.google.android.material.R.attr.colorOnTertiaryContainer),
            background = color(android.R.attr.colorBackground),
            onBackground = color(com.google.android.material.R.attr.colorOnBackground),
            surface = color(com.google.android.material.R.attr.colorSurface),
            onSurface = color(com.google.android.material.R.attr.colorOnSurface),
            surfaceVariant = color(com.google.android.material.R.attr.colorSurfaceVariant),
            onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
            surfaceContainerLowest = color(com.google.android.material.R.attr.colorSurfaceContainerLowest),
            surfaceContainerLow = color(com.google.android.material.R.attr.colorSurfaceContainerLow),
            surfaceContainer = color(com.google.android.material.R.attr.colorSurfaceContainer),
            surfaceContainerHigh = color(com.google.android.material.R.attr.colorSurfaceContainerHigh),
            surfaceContainerHighest = color(com.google.android.material.R.attr.colorSurfaceContainerHighest),
            outline = color(com.google.android.material.R.attr.colorOutline),
            outlineVariant = color(com.google.android.material.R.attr.colorOutlineVariant),
            error = color(android.R.attr.colorError),
            onError = color(com.google.android.material.R.attr.colorOnError),
            errorContainer = color(com.google.android.material.R.attr.colorErrorContainer),
            onErrorContainer = color(com.google.android.material.R.attr.colorOnErrorContainer),
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
