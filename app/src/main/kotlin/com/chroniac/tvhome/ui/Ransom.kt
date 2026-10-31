package com.chroniac.tvhome.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

internal class Glyph(
    val layout: TextLayoutResult,
    val centerX: Float,
    val boxW: Float,
    val boxH: Float,
    val dy: Float,
    val tilt: Float,
    val inverted: Boolean,
    /** The letter's ink bounds, relative to its layout's top-left. */
    val ink: Rect,
) {
    /** Top and bottom of the letter's ink in word space (the glyph is drawn centred on [centerX], [dy]). */
    val inkTop: Float get() = dy - layout.size.height / 2f + ink.top
    val inkBottom: Float get() = dy - layout.size.height / 2f + ink.bottom
}

/**
 * A label cut into per-letter pieces in mixed faces, ransom-note style. Geometry in px with the
 * origin at the word's left edge, vertically centred; [inkTop]..[inkBottom] is where the letters
 * themselves are (the line boxes behind [height] are much taller).
 */
internal class RansomWord(val glyphs: List<Glyph>, val width: Float, val height: Float, val inkTop: Float, val inkBottom: Float) {
    companion object {
        /**
         * [outlined] cuts for [drawRansomOutlined], P5's command-menu look: narrow white letters of
         * one cap height packed edge to edge so their black pieces merge into one backing, most
         * vowels in lower case; [fonts] is needed for it, the pieces hug each letter's ink.
         * Otherwise letters get room for the alternating paper/ink boxes of [drawRansomBoxes].
         */
        fun cut(
            label: String,
            measurer: TextMeasurer,
            density: Density,
            size: TextUnit = 44.sp,
            outlined: Boolean = false,
            fonts: FontFamily.Resolver? = null,
        ): RansomWord =
            with(density) {
                val faces = if (outlined) {
                    // Sizes and squeezes measured on the fonts so that every face gets the game's
                    // proportions: cap height 0.725 x size, advance about 0.58 x size per letter.
                    listOf(
                        TextStyle(fontFamily = Fonts.dela, fontSize = size, textGeometricTransform = TextGeometricTransform(scaleX = 0.68f)),
                        TextStyle(fontFamily = Fonts.russo, fontSize = size * 1.05f, textGeometricTransform = TextGeometricTransform(scaleX = 0.86f)),
                        TextStyle(fontFamily = Fonts.oswald, fontWeight = FontWeight.Bold, fontSize = size * 0.92f, textGeometricTransform = TextGeometricTransform(scaleX = 1.1f)),
                    )
                } else {
                    listOf(
                        TextStyle(fontFamily = Fonts.dela, fontSize = size),
                        TextStyle(fontFamily = Fonts.russo, fontSize = size * 1.09f),
                        TextStyle(fontFamily = Fonts.oswald, fontWeight = FontWeight.Bold, fontSize = size * 1.18f),
                    )
                }
                val unit = size.toPx() / 44.sp.toPx() // keeps paddings proportional to the letter size
                val pad = (if (outlined) 0.dp else 6.dp).toPx() * unit
                val gap = (if (outlined) (-2).dp else 3.dp).toPx() * unit
                val space = (if (outlined) 12.dp else 18.dp).toPx() * unit
                var x = 0f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val bounds = android.graphics.Rect()
                var height = 0f
                var inkTop = Float.POSITIVE_INFINITY
                var inkBottom = Float.NEGATIVE_INFINITY
                val glyphs = ArrayList<Glyph>(label.length)
                label.uppercase().forEachIndexed { i, ch ->
                    if (ch.isWhitespace()) {
                        x += space
                        return@forEachIndexed
                    }
                    // Deterministic per-letter variation: the same word always gets the same cut.
                    val seed = i * 7919 + ch.code * 31
                    val lower = outlined && i > 0 && ch in VOWELS && seed % 3 != 0
                    val text = (if (lower) ch.lowercaseChar() else ch).toString()
                    val face = faces[seed / 7 % faces.size]
                    val layout = measurer.measure(text, face)
                    val boxW = layout.size.width + pad * 2
                    val boxH = layout.size.height * 0.92f
                    val dy = (seed % 7 - 3) * (if (outlined) 0.8.dp else 1.8.dp).toPx() * unit
                    val ink = if (fonts == null) {
                        Rect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat())
                    } else {
                        inkBounds(text, face, layout, fonts, paint, bounds)
                    }
                    glyphs += Glyph(
                        layout = layout,
                        centerX = x + boxW / 2,
                        boxW = boxW,
                        boxH = boxH,
                        dy = dy,
                        // The game's menu letters are cut almost straight; the collage tilts them more.
                        tilt = if (outlined) (seed % 7 - 3) * 1.4f else (seed % 11 - 5) * 1.6f,
                        inverted = !outlined && i % 2 == 1,
                        ink = ink,
                    )
                    x += boxW + gap
                    height = max(height, boxH)
                    inkTop = min(inkTop, glyphs.last().inkTop)
                    inkBottom = max(inkBottom, glyphs.last().inkBottom)
                }
                if (glyphs.isEmpty()) {
                    inkTop = 0f
                    inkBottom = 0f
                }
                RansomWord(glyphs, max(x, 1f), height, inkTop, inkBottom)
            }

        /** Letters P5 sets in lower case inside its menu words ("SkiLL", "MiSSiON", "COOPERaTioN"). */
        private val VOWELS = setOf('A', 'E', 'I', 'U', 'Y', 'А', 'Е', 'Ё', 'И', 'У', 'Ы', 'Э', 'Ю', 'Я')
    }
}

/**
 * A letter's real ink bounds (Paint.getTextBounds with the same typeface, size and squeeze) in its
 * layout's coordinates. Compose's getPathForRange would give the selection box, a full line high.
 */
private fun Density.inkBounds(text: String, face: TextStyle, layout: TextLayoutResult, fonts: FontFamily.Resolver, paint: Paint, out: android.graphics.Rect): Rect {
    paint.typeface = fonts.resolveAsTypeface(face.fontFamily, face.fontWeight ?: FontWeight.Normal).value
    paint.textSize = face.fontSize.toPx()
    paint.textScaleX = face.textGeometricTransform?.scaleX ?: 1f
    paint.getTextBounds(text, 0, text.length, out)
    val baseline = layout.firstBaseline
    return Rect(out.left.toFloat(), baseline + out.top, out.right.toFloat(), baseline + out.bottom)
}

/** Letters on alternating paper/ink boxes, each popping in after the previous one. */
internal fun DrawScope.drawRansomBoxes(word: RansomWord, palette: Palette, time: Double, startedAt: Double, delay: Float = 0.05f) {
    word.glyphs.forEachIndexed { k, glyph ->
        val p = easeOutBack(progress(time, startedAt, 0.2f, delay = delay + 0.03f * k), 2.4f)
        if (p <= 0f) return@forEachIndexed
        withTransform({
            translate(glyph.centerX, glyph.dy)
            rotate(glyph.tilt, Offset.Zero)
            scale(p, p, Offset.Zero)
        }) {
            drawRect(
                if (glyph.inverted) palette.paper else palette.ink,
                topLeft = Offset(-glyph.boxW / 2, -glyph.boxH / 2),
                size = Size(glyph.boxW, glyph.boxH),
            )
            drawText(glyph.layout, color = if (glyph.inverted) palette.ink else palette.paper, topLeft = glyph.centered())
        }
    }
}

/** Only the letters of [word] in [color], for recolouring part of a word drawn by [drawRansomOutlined]. */
internal fun DrawScope.drawRansomLetters(word: RansomWord, color: Color) {
    for (glyph in word.glyphs) placed(glyph) { origin -> drawText(glyph.layout, color = color, topLeft = origin) }
}

/**
 * P5's command-menu lettering: every letter in paper, cut out on its own black piece (its ink
 * bounds plus [pad], sizes varying a little). All black pieces go down first, so they merge into one
 * jagged backing and never cover a letter. The game's command list has no inverted letters.
 */
internal fun DrawScope.drawRansomOutlined(word: RansomWord, palette: Palette, pad: Float) {
    word.glyphs.forEachIndexed { k, glyph ->
        placed(glyph) { origin -> drawPiece(glyph, origin, pad * PAD_JITTER[k % PAD_JITTER.size], palette.ink) }
    }
    for (glyph in word.glyphs) placed(glyph) { origin -> drawText(glyph.layout, color = palette.paper, topLeft = origin) }
}

/** Black-piece padding per letter, cycling: hand-cut pieces are never quite the same size. */
private val PAD_JITTER = floatArrayOf(1f, 1.3f, 0.85f, 1.15f)

private fun DrawScope.drawPiece(glyph: Glyph, origin: Offset, pad: Float, color: Color) {
    drawRect(
        color,
        topLeft = Offset(origin.x + glyph.ink.left - pad, origin.y + glyph.ink.top - pad),
        size = Size(glyph.ink.width + 2 * pad, glyph.ink.height + 2 * pad),
    )
}

private inline fun DrawScope.placed(glyph: Glyph, block: DrawScope.(origin: Offset) -> Unit) {
    withTransform({
        translate(glyph.centerX, glyph.dy)
        rotate(glyph.tilt, Offset.Zero)
    }) { block(glyph.centered()) }
}

private fun Glyph.centered() = Offset(-layout.size.width / 2f, -layout.size.height / 2f)
