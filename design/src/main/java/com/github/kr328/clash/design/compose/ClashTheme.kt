package com.github.kr328.clash.design.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.kr328.clash.design.R

/**
 * Compose counterpart of the app's Material 3 Expressive XML palette.
 *
 * The color roles deliberately reference the existing resource tokens so the
 * Compose migration keeps the product's established light and dark branding.
 */
@Composable
fun ClashTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.color_clash_dark),
            onPrimary = Color.White,
            primaryContainer = colorResource(R.color.color_primary_container_dark),
            onPrimaryContainer = colorResource(R.color.color_on_primary_container_dark),
            secondary = colorResource(R.color.color_secondary_dark),
            onSecondary = colorResource(R.color.color_on_secondary_dark),
            secondaryContainer = colorResource(R.color.color_secondary_container_dark),
            onSecondaryContainer = colorResource(R.color.color_on_secondary_container_dark),
            tertiary = colorResource(R.color.color_tertiary_dark),
            onTertiary = colorResource(R.color.color_on_tertiary_dark),
            background = colorResource(R.color.color_dark_background),
            onBackground = colorResource(R.color.color_on_surface_dark),
            surface = colorResource(R.color.color_dark_surface),
            onSurface = colorResource(R.color.color_on_surface_dark),
            surfaceVariant = colorResource(R.color.color_surface_container_high_dark),
            onSurfaceVariant = colorResource(R.color.color_on_surface_dark),
            error = colorResource(R.color.color_error),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.color_clash_light),
            onPrimary = Color.White,
            primaryContainer = colorResource(R.color.color_primary_container_light),
            onPrimaryContainer = colorResource(R.color.color_on_primary_container_light),
            secondary = colorResource(R.color.color_secondary_light),
            onSecondary = Color.White,
            secondaryContainer = colorResource(R.color.color_secondary_container_light),
            onSecondaryContainer = colorResource(R.color.color_on_secondary_container_light),
            tertiary = colorResource(R.color.color_tertiary_light),
            background = colorResource(R.color.color_light_background),
            onBackground = colorResource(R.color.color_on_surface_light),
            surface = colorResource(R.color.color_surface_light),
            onSurface = colorResource(R.color.color_on_surface_light),
            surfaceVariant = colorResource(R.color.color_surface_container_high_light),
            onSurfaceVariant = colorResource(R.color.color_on_surface_light),
            error = colorResource(R.color.color_error),
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
