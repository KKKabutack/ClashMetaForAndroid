package com.github.kr328.clash.design.compose

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * The unified creamy-white glass fill of the app's design language, theme-aware.
 * Reads as "milk glass" against the darkened page background; alpha keeps the
 * background faintly visible through every glass surface.
 */
@Composable
internal fun rememberGlassCream(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        scheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
}

/**
 * The accented glass fill for highlighted / selected surfaces (same material as
 * [rememberGlassCream], tinted with the scheme's secondary container).
 */
@Composable
internal fun rememberGlassAccent(): Color {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (scheme.background.luminance() < 0.5f) 0.55f else 0.80f
    return scheme.secondaryContainer.copy(alpha = alpha)
}

/**
 * Painted Liquid Glass material for surfaces with nothing meaningful behind them
 * (list cards, chips, in-flow buttons): tinted fill, a top-lit sheen, and a bright
 * rim stroke fading to a dark bottom edge. Visually matches the shader-based
 * [liquidGlass] material at a fraction of the cost.
 *
 * No elevation shadow: the fill is translucent, and the system-rendered shadow
 * (a coarse polygon tessellation) would show through it under the glass.
 */
internal fun Modifier.glassSurface(
    shape: Shape,
    tint: Color,
): Modifier = this
    .clip(shape)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        // The tint doubles as a theme proxy: light themes use bright fills, dark
        // themes dark ones. White sheen that reads as subtle on a light background
        // becomes a harsh top-to-down gradient on dark, so dark themes keep the
        // top lighting and rim barely-there and lean on the dark bottom edge instead.
        val dark = tint.luminance() < 0.5f
        drawOutline(outline, color = tint)
        drawOutline(
            outline,
            brush = if (dark) {
                // Top lighting confined to the upper edge zone; the rest stays the
                // flat tint so tall cards don't read as a bright-to-dark banner.
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.08f),
                    0.35f to Color.White.copy(alpha = 0.015f),
                    1f to Color.Black.copy(alpha = 0.05f),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.04f),
                    ),
                )
            },
        )
        drawOutline(
            outline,
            brush = if (dark) {
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.22f),
                    0.40f to Color.White.copy(alpha = 0.04f),
                    1f to Color.Black.copy(alpha = 0.20f),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Black.copy(alpha = 0.12f),
                    ),
                )
            },
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/**
 * Glass card used across pages; [tint] picks the cream or accented variant.
 * The tap ripple is bounded by [shape], so it never clashes with the glass rim.
 */
@Composable
internal fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = rememberGlassCream(),
    content: @Composable () -> Unit,
) {
    val glass = Modifier.glassSurface(shape = shape, tint = tint)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(glass),
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier.then(glass),
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            content()
        }
    }
}

/**
 * Liquid Glass material tinted with the current ClashTheme colors.
 *
 * Starts from [GlassStyle.regular] — edge refraction, specular highlight, dark rim,
 * and size-adaptive blur — then applies the theme-tinted fill and window background
 * so light and dark schemes (including Force Light / Force Dark) adapt automatically.
 *
 * [tint] carries its own alpha; [rememberGlassCream] is the design-language default.
 * [pressScale] adds a Liquid-Glass-style squash while the material is pressed; only
 * takes effect when the companion [liquidGlass] modifier receives an [InteractionSource].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun rememberLiquidGlassStyle(
    tint: Color = rememberGlassCream(),
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    pressScale: Float? = null,
): GlassStyle {
    val backgroundColor = MaterialTheme.colorScheme.background

    return GlassStyle.regular.then {
        shape(shape)
        tint(tint)
        backgroundColor(backgroundColor)
        if (pressScale != null) {
            pressed { scale(pressScale) }
        }
    }
}

/**
 * Applies the Liquid Glass effect over content captured from [state].
 *
 * The [style]'s shape drives both refraction and masking, so no external clip is
 * required. When [interactionSource] is supplied, the material squashes toward the
 * pointer and its content (per [GlassTransformTarget.MaterialAndContent]) while pressed.
 */
@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.liquidGlass(
    state: HazeState,
    style: GlassStyle,
    interactionSource: InteractionSource? = null,
): Modifier = hazeGlass(
    input = HazeInput.Sources(state),
    style = style,
    interactionSource = interactionSource,
    interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
)

/** Nudges a resolved scheme color darker so glass surfaces stand out. */
internal fun darkenForGlass(color: Color, fraction: Float): Color = lerp(color, Color.Black, fraction)
