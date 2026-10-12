package com.bragincad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import kotlin.math.min
import kotlin.math.round

enum class PixelFrameMode { STEPPED, BEVELED, RECTANGULAR }
enum class PixelFrameAccentPlacement { NONE, TOP_LEFT, ALL }

data class PixelFrameConfig(
    val mode: PixelFrameMode,
    val cornerSize: Dp,
    val pixelStep: Dp = 0.dp,
    val cornerSteps: Int = 0,
    val borderWidth: Dp,
    val borderColor: Color,
    val backgroundColor: Color,
    val shadowColor: Color = Color.Transparent,
    val shadowOffsetX: Dp = 0.dp,
    val shadowOffsetY: Dp = 0.dp,
    val shadowDepth: Dp = 0.dp,
    val cornerAccentColor: Color = Color.Transparent,
    val cornerAccentWidth: Dp = 0.dp,
    val cornerAccentLength: Dp = 0.dp,
    val cornerAccentPlacement: PixelFrameAccentPlacement = PixelFrameAccentPlacement.NONE,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val enabled: Boolean = true,
    val pressed: Boolean = false,
)

object PixelFrameStyle {
    val Primary = PixelFrameConfig(
        mode = PixelFrameMode.STEPPED,
        cornerSize = 12.dp,
        pixelStep = 3.dp,
        cornerSteps = 4,
        borderWidth = 3.dp,
        borderColor = Color(0xFF08121B),
        backgroundColor = Color(0xFFF2F3F5),
        shadowColor = Color(0xFF2B3747),
        shadowOffsetY = 3.dp,
        shadowDepth = 3.dp,
        cornerAccentColor = Color(0xFFF5293D),
        cornerAccentWidth = 3.dp,
        cornerAccentLength = 14.dp,
        cornerAccentPlacement = PixelFrameAccentPlacement.ALL,
        contentPadding = PaddingValues(horizontal = 18.dp),
    )

    val Panel = PixelFrameConfig(
        mode = PixelFrameMode.BEVELED,
        cornerSize = 9.dp,
        borderWidth = 1.dp,
        borderColor = Color(0xFF354454),
        backgroundColor = Color(0xFF111B25),
        cornerAccentColor = Color(0xFFF5293D),
        cornerAccentWidth = 2.dp,
        cornerAccentLength = 24.dp,
        cornerAccentPlacement = PixelFrameAccentPlacement.TOP_LEFT,
        contentPadding = PaddingValues(16.dp),
    )

    val Card = PixelFrameConfig(
        mode = PixelFrameMode.BEVELED,
        cornerSize = 6.dp,
        borderWidth = 1.dp,
        borderColor = Color(0xFF354454),
        backgroundColor = Color(0xFF111B25),
        contentPadding = PaddingValues(horizontal = 11.dp),
    )
}

private class PixelFrameShape(private val config: PixelFrameConfig) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(pixelFramePath(size.width, size.height, pixelCornerPx(config, density), config))
}

private fun pixelCornerPx(config: PixelFrameConfig, density: Density): Float = with(density) {
    if (config.mode == PixelFrameMode.STEPPED && config.pixelStep > 0.dp && config.cornerSteps > 0)
        round(config.pixelStep.toPx()).coerceAtLeast(1f) * config.cornerSteps
    else config.cornerSize.toPx()
}

private fun pixelFramePath(width: Float, height: Float, requestedCorner: Float, config: PixelFrameConfig): Path {
    val maxCorner = min(width, height) / 2f
    val corner = requestedCorner.coerceIn(0f, maxCorner)
    return Path().apply {
        when (config.mode) {
            PixelFrameMode.RECTANGULAR -> {
                moveTo(0f, 0f); lineTo(width, 0f); lineTo(width, height); lineTo(0f, height)
            }
            PixelFrameMode.BEVELED -> {
                moveTo(corner, 0f); lineTo(width - corner, 0f); lineTo(width, corner)
                lineTo(width, height - corner); lineTo(width - corner, height)
                lineTo(corner, height); lineTo(0f, height - corner); lineTo(0f, corner)
            }
            PixelFrameMode.STEPPED -> {
                val steps = config.cornerSteps.coerceAtLeast(1)
                val step = corner / steps
                moveTo(corner, 0f); lineTo(width - corner, 0f)
                for (i in 1..steps) { lineTo(width - corner + step * i, step * (i - 1)); lineTo(width - corner + step * i, step * i) }
                lineTo(width, height - corner)
                for (i in 1..steps) { lineTo(width - step * (i - 1), height - corner + step * i); lineTo(width - step * i, height - corner + step * i) }
                lineTo(corner, height)
                for (i in 1..steps) { lineTo(corner - step * i, height - step * (i - 1)); lineTo(corner - step * i, height - step * i) }
                lineTo(0f, corner)
                for (i in 1..steps) { lineTo(step * (i - 1), corner - step * i); lineTo(step * i, corner - step * i) }
            }
        }
        close()
    }
}

private fun pixelFrameAccentPaths(width: Float, height: Float, requestedCorner: Float, length: Float, config: PixelFrameConfig): List<Path> {
    val corner = requestedCorner.coerceIn(0f, min(width, height) / 2f)
    val run = length.coerceAtMost((min(width, height) / 2f - corner).coerceAtLeast(0f))
    fun steppedTopLeft() = Path().apply {
        val steps = config.cornerSteps.coerceAtLeast(1); val step = corner / steps
        moveTo(corner + run, 0f); lineTo(corner, 0f)
        for (i in steps downTo 1) { lineTo(step * (i - 1), corner - step * i); lineTo(step * (i - 1), corner - step * (i - 1)) }
        lineTo(0f, corner + run)
    }
    fun steppedTopRight() = Path().apply {
        val steps = config.cornerSteps.coerceAtLeast(1); val step = corner / steps
        moveTo(width - corner - run, 0f); lineTo(width - corner, 0f)
        for (i in 1..steps) { lineTo(width - corner + step * i, step * (i - 1)); lineTo(width - corner + step * i, step * i) }
        lineTo(width, corner + run)
    }
    fun steppedBottomRight() = Path().apply {
        val steps = config.cornerSteps.coerceAtLeast(1); val step = corner / steps
        moveTo(width, height - corner - run); lineTo(width, height - corner)
        for (i in 1..steps) { lineTo(width - step * (i - 1), height - corner + step * i); lineTo(width - step * i, height - corner + step * i) }
        lineTo(width - corner - run, height)
    }
    fun steppedBottomLeft() = Path().apply {
        val steps = config.cornerSteps.coerceAtLeast(1); val step = corner / steps
        moveTo(corner + run, height); lineTo(corner, height)
        for (i in 1..steps) { lineTo(corner - step * i, height - step * (i - 1)); lineTo(corner - step * i, height - step * i) }
        lineTo(0f, height - corner - run)
    }
    fun beveledTopLeft() = Path().apply {
        moveTo(corner + run, 0f); lineTo(corner, 0f); lineTo(0f, corner); lineTo(0f, corner + run)
    }
    val topLeft = if (config.mode == PixelFrameMode.STEPPED) steppedTopLeft() else beveledTopLeft()
    if (config.cornerAccentPlacement == PixelFrameAccentPlacement.TOP_LEFT) return listOf(topLeft)
    if (config.cornerAccentPlacement != PixelFrameAccentPlacement.ALL) return emptyList()
    if (config.mode == PixelFrameMode.STEPPED) return listOf(topLeft, steppedTopRight(), steppedBottomRight(), steppedBottomLeft())
    return listOf(
        topLeft,
        Path().apply { moveTo(width - corner - run, 0f); lineTo(width - corner, 0f); lineTo(width, corner); lineTo(width, corner + run) },
        Path().apply { moveTo(width, height - corner - run); lineTo(width, height - corner); lineTo(width - corner, height); lineTo(width - corner - run, height) },
        Path().apply { moveTo(corner + run, height); lineTo(corner, height); lineTo(0f, height - corner); lineTo(0f, height - corner - run) },
    )
}

@Composable
fun PixelFrame(
    config: PixelFrameConfig,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    val shape = remember(config, density) { PixelFrameShape(config) }
    val pressOffset = if (isPressed || config.pressed) 2.dp else 0.dp
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(
        enabled = config.enabled,
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
    Box(
        modifier
            .padding(bottom = config.shadowDepth + config.shadowOffsetY.coerceAtLeast(0.dp))
            .drawBehind {
                val cornerPx = pixelCornerPx(config, density)
                val borderPx = with(density) { config.borderWidth.toPx() }
                val accentPx = with(density) { config.cornerAccentWidth.toPx() }
                val accentLengthPx = with(density) { config.cornerAccentLength.toPx() }
                val dx = with(density) { config.shadowOffsetX.toPx() }
                val dy = with(density) { (config.shadowOffsetY + pressOffset).toPx() }
                val framePath = pixelFramePath(size.width, size.height, cornerPx, config)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    fun paint(color: Color, style: Paint.Style, width: Float = 0f, antiAlias: Boolean = true) = Paint().apply {
                        this.color = color.toArgb(); this.style = style; strokeWidth = width
                        strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.SQUARE; isAntiAlias = antiAlias
                    }
                    val crisp = config.mode != PixelFrameMode.STEPPED
                    if (config.shadowDepth > 0.dp) {
                        native.save(); native.translate(dx, dy)
                        native.drawPath(framePath.asAndroidPath(), paint(config.shadowColor, Paint.Style.FILL, antiAlias = crisp)); native.restore()
                    }
                    native.drawPath(framePath.asAndroidPath(), paint(config.backgroundColor, Paint.Style.FILL, antiAlias = crisp))
                    if (borderPx > 0f) native.drawPath(framePath.asAndroidPath(), paint(config.borderColor, Paint.Style.STROKE, borderPx, crisp))
                    if (accentPx > 0f && config.cornerAccentPlacement != PixelFrameAccentPlacement.NONE) {
                        val accentPaint = paint(config.cornerAccentColor, Paint.Style.STROKE, accentPx, crisp)
                        pixelFrameAccentPaths(size.width, size.height, cornerPx, accentLengthPx, config).forEach { accentPath ->
                            native.drawPath(accentPath.asAndroidPath(), accentPaint)
                        }
                    }
                }
            }
            .clip(shape)
            .then(clickableModifier)
            .padding(top = pressOffset)
            .padding(config.contentPadding),
    ) { content() }
}
