package com.taurus.android.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Liquid Glass for Android.
 *
 * iOS Liquid Glass = blurred backdrop + translucent fill + specular highlight + rim light.
 * Compose has no backdrop-blur primitive, so a [GlassScene] records its background into one
 * [GraphicsLayer]; every [LiquidGlassSurface] inside redraws that layer, shifted to line up with
 * the scene, through its own blur layer. Blur needs API 31+ (RenderEffect). Below that the
 * surface falls back to a more opaque frosted fill with the same highlight and rim.
 *
 * Limit: surfaces re-record when they redraw, not when the background changes by itself, so a
 * moving background needs the surface invalidated too (a static or scroll-independent
 * background is fine).
 */
class GlassSceneState internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

private val LocalGlassScene = staticCompositionLocalOf<GlassSceneState?> { null }

/** Hosts the background that glass surfaces blur. Put every glass surface inside [content]. */
@Composable
fun GlassScene(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scene = remember(layer) { GlassSceneState(layer) }
    CompositionLocalProvider(LocalGlassScene provides scene) {
        Box(modifier.onGloballyPositioned { scene.origin = it.positionInRoot() }) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                content = background,
            )
            content()
        }
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    fillAlpha: Float = 0.14f,
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val scene = LocalGlassScene.current
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shape = RoundedCornerShape(cornerRadius)
    val fill = if (blurSupported) fillAlpha else (fillAlpha + 0.14f).coerceAtMost(1f)

    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                val sceneLayer = scene?.layer
                if (sceneLayer != null) {
                    val offset = position - scene.origin
                    val radius = blurRadius.toPx()
                    blurLayer.renderEffect =
                        if (blurSupported && radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
                    blurLayer.record { translate(-offset.x, -offset.y) { drawLayer(sceneLayer) } }
                    drawLayer(blurLayer)
                }

                drawRect(tint.copy(alpha = fill))

                // specular highlight, top-left
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.10f),
                        radius = max(w, h) * 0.65f,
                    ),
                )

                // depth cue, bottom edge
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                        startY = h * 0.55f,
                        endY = h,
                    ),
                )

                // rim light
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(tint.copy(alpha = 0.70f), tint.copy(alpha = 0.08f), tint.copy(alpha = 0.35f)),
                        start = Offset.Zero,
                        end = Offset(w, h),
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            },
        content = content,
    )
}

/** Glass tuned for dark scenes, which is what Taurus draws on. */
@Composable
fun AdaptiveLiquidGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        fillAlpha = 0.12f,
        blurRadius = 28.dp,
        tint = Color(0xFFD6E4FF),
        content = content,
    )
}
