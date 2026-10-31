package com.chroniac.tvhome.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.Entry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Per-row tilt and horizontal jitter, cycling, so the list reads as a hand-cut collage. */
private val TILT = floatArrayOf(-7f, 3f, -4f, 5f, -2f, 6f, -5f, 2f)
private val JITTER_DP = floatArrayOf(0f, 26f, 6f, 36f, 14f, 30f, 2f, 20f)
private const val VISIBLE_RADIUS = 3.8f

/**
 * Collage theme menu: big tilted words cascading diagonally; the selected one is re-cut as
 * ransom-note letters on a jagged black-and-white spotlight with a bobbing cursor.
 */
@Composable
fun MenuList(
    entries: List<Entry>,
    state: MenuState,
    time: DoubleState,
    palette: Palette,
    anchorY: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val density = LocalDensity.current
    val words = remember(entries) {
        val style = TextStyle(fontFamily = Fonts.dela, fontSize = 32.sp)
        entries.map { measurer.measure(it.label.uppercase(), style) }
    }
    val selectedLabel = entries.getOrNull(state.selected)?.label.orEmpty()
    val ransom = remember(selectedLabel, density) { RansomWord.cut(selectedLabel, measurer, density) }
    val spot = remember(ransom, density) { Spotlight(ransom, density) }

    Spacer(modifier.fillMaxSize().drawWithCache {
        val anchor = Offset(size.width * 0.12f, size.height * anchorY)
        val spacing = 72.dp.toPx()
        val diagonal = 20.dp.toPx()
        val shadow = 5.dp.toPx()
        val margin = 20.dp.toPx()
        val maxWordWidth = size.width - anchor.x - 2 * margin
        val jitter = FloatArray(JITTER_DP.size) { JITTER_DP[it].dp.toPx() }

        onDrawBehind {
            val t = time.doubleValue
            val scroll = state.scrollAt(t)
            for (i in entries.indices) {
                if (i == state.selected) continue
                val r = i - scroll
                if (abs(r) > VISIBLE_RADIUS) continue
                val alpha = (1f - 0.26f * abs(r)).coerceIn(0f, 1f)
                val word = words[i]
                val x = anchor.x + r * diagonal + jitter[i % jitter.size]
                // Long names shrink to the space left of the screen edge instead of being cut off.
                val zoom = max(0.6f, 1f - 0.1f * abs(r)) * min(1f, (size.width - x - margin) / word.size.width)
                val lift = -word.size.height / 2f
                withTransform({
                    translate(x, anchor.y + r * spacing)
                    rotate(TILT[i % TILT.size], Offset.Zero)
                    scale(zoom, zoom, Offset.Zero)
                }) {
                    drawText(word, color = palette.ink, topLeft = Offset(shadow, lift + shadow), alpha = alpha)
                    drawText(word, color = palette.paper, topLeft = Offset(0f, lift), alpha = alpha)
                }
            }
            val r = state.selected - scroll
            drawSelected(
                word = ransom,
                spot = spot,
                origin = Offset(anchor.x + r * diagonal, anchor.y + r * spacing),
                tilt = TILT[state.selected % TILT.size] * 0.5f,
                fit = min(1f, maxWordWidth / ransom.width),
                time = t,
                startedAt = state.selectedAt,
                palette = palette,
            )
        }
    })
}

private fun DrawScope.drawSelected(
    word: RansomWord,
    spot: Spotlight,
    origin: Offset,
    tilt: Float,
    fit: Float,
    time: Double,
    startedAt: Double,
    palette: Palette,
) {
    val pop = easeOutBack(progress(time, startedAt, 0.24f), 2.2f)
    withTransform({
        translate(origin.x, origin.y)
        rotate(tilt + wave(time, 2.2) * 0.8f, Offset.Zero)
        scale(fit, fit, Offset.Zero)
    }) {
        withTransform({ scale(pop, 1f, Offset(spot.pivot, 0f)) }) {
            drawPath(spot.outer, palette.paper)
            drawPath(spot.inner, palette.ink)
        }
        translate(wave(time, 6.0) * 6.dp.toPx(), 0f) {
            drawPath(spot.cursorOutline, palette.ink)
            drawPath(spot.cursor, palette.accent)
        }
        drawRansomBoxes(word, palette, time, startedAt)
    }
}

/** Jagged two-layer spotlight and cursor sized to a ransom word (px). */
private class Spotlight(word: RansomWord, density: Density) {
    val outer: Path
    val inner: Path
    val pivot: Float
    val cursor: Path
    val cursorOutline: Path

    init {
        with(density) {
            val w = word.width
            val h = word.height / 2
            outer = polygon(
                Offset(-44.dp.toPx(), -h * 1.45f),
                Offset(w + 40.dp.toPx(), -h * 1.75f),
                Offset(w + 22.dp.toPx(), h * 1.6f),
                Offset(-30.dp.toPx(), h * 1.35f),
            )
            inner = polygon(
                Offset(-32.dp.toPx(), -h * 1.18f),
                Offset(w + 26.dp.toPx(), -h * 1.42f),
                Offset(w + 12.dp.toPx(), h * 1.3f),
                Offset(-20.dp.toPx(), h * 1.1f),
            )
            pivot = -44.dp.toPx()
            cursor = polygon(
                Offset(-78.dp.toPx(), -22.dp.toPx()),
                Offset(-32.dp.toPx(), 2.dp.toPx()),
                Offset(-74.dp.toPx(), 22.dp.toPx()),
                Offset(-62.dp.toPx(), 0f),
            )
            cursorOutline = polygon(
                Offset(-86.dp.toPx(), -30.dp.toPx()),
                Offset(-22.dp.toPx(), 2.dp.toPx()),
                Offset(-82.dp.toPx(), 30.dp.toPx()),
                Offset(-70.dp.toPx(), 0f),
            )
        }
    }
}
