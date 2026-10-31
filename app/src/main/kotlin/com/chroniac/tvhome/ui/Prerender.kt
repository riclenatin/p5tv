package com.chroniac.tvhome.ui

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Renders static artwork once into a bitmap. Each frame then costs one textured quad instead of
 * re-rasterising large anti-aliased paths, which kept a CPU core busy on this TV's RenderThread.
 */
fun CacheDrawScope.prerender(
    width: Int,
    height: Int,
    config: ImageBitmapConfig,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val bitmap = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), config)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat()), block)
    return bitmap
}
