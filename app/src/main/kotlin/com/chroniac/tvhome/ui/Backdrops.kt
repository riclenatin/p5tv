package com.chroniac.tvhome.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Collage theme backdrop, rendered once per size into an opaque RGB_565 bitmap. */
@Composable
fun CollageBackdrop(palette: Palette, modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxSize().drawWithCache {
        val image = prerender(size.width.roundToInt(), size.height.roundToInt(), ImageBitmapConfig.Rgb565) {
            val p = palette
            val w = size.width
            val h = size.height
            drawRect(p.backdrop)
            drawPath(polygon(Offset(w * 0.38f, 0f), Offset(w * 0.55f, 0f), Offset(w * 0.33f, h), Offset(w * 0.16f, h)), p.deep, alpha = 0.55f)
            translate(w * 0.22f, h * 0.60f) { drawPath(sunburst(hypot(w, h), 18), p.ink, alpha = 0.10f) }
            halftone(p.ink, alpha = 0.18f, band = polygon(Offset(0f, h * 0.42f), Offset(w * 0.42f, 0f), Offset(w * 0.78f, 0f), Offset(w * 0.18f, h)))
            drawPath(polygon(Offset(w * 0.50f, h), Offset(w, h * 0.55f), Offset(w, h)), p.ink)
            drawPath(polygon(Offset(0f, 0f), Offset(w * 0.40f, 0f), Offset(w * 0.30f, h * 0.10f), Offset(0f, h * 0.34f)), p.ink)
            drawPath(polygon(Offset(w * 0.335f, h), Offset(w * 0.342f, h), Offset(w * 0.64f, 0f), Offset(w * 0.633f, 0f)), p.paper, alpha = 0.9f)
        }
        onDrawBehind { drawImage(image) }
    })
}

// ---- shared shapes ----------------------------------------------------------------------------

/** Regular dot screen clipped to [band]. */
internal fun DrawScope.halftone(color: Color, alpha: Float, band: Path, tilePx: Int = 22, radiusPx: Float = 4.5f) {
    val tile = ImageBitmap(tilePx, tilePx)
    Canvas(tile).drawCircle(Offset(tilePx / 2f, tilePx / 2f), radiusPx, Paint().apply { this.color = color })
    clipPath(band) { drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = alpha) }
}

internal fun sunburst(radius: Float, count: Int): Path = Path().apply {
    val step = (2 * PI / count).toFloat()
    repeat(count) { k ->
        val a0 = k * step
        val a1 = a0 + step / 2
        moveTo(0f, 0f)
        lineTo(radius * cos(a0), radius * sin(a0))
        lineTo(radius * cos(a1), radius * sin(a1))
        close()
    }
}

/** Irregular comic-style explosion; [seed] varies the spike lengths deterministically. */
internal fun burst(center: Offset, inner: Float, outer: Float, spikes: Int, seed: Float = 1f): Path = Path().apply {
    val step = (2 * PI / (spikes * 2)).toFloat()
    for (k in 0 until spikes * 2) {
        val jitter = 1f + 0.16f * sin(k * 12.9898f + seed * 78.233f)
        val r = (if (k % 2 == 0) outer else inner) * jitter
        val a = k * step - 0.3f
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Five-point star. */
internal fun star(center: Offset, outer: Float, inner: Float, rotationDeg: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = (rotationDeg / 180f * PI + k * PI / 5 - PI / 2).toFloat()
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
