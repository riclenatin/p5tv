package com.chroniac.tvhome.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.AppEntry
import com.chroniac.tvhome.Entry
import com.chroniac.tvhome.ShortcutEntry
import kotlin.math.pow

private const val ENTER_SECONDS = 0.34f

/**
 * Collage theme: the selected entry's art in a tilted white frame with a jagged shadow and a
 * name plate (plus the bond line). Slides in with overshoot and a white flash on every change.
 */
@Composable
fun Portrait(
    entry: Entry?,
    bondLine: String?,
    selectedAt: Double,
    time: DoubleState,
    palette: Palette,
    modifier: Modifier = Modifier,
) {
    val shortcutIcon = (entry as? ShortcutEntry)?.let { painterResource(it.iconRes) }
    val measurer = rememberTextMeasurer()
    val plate = remember(entry?.label) {
        entry?.let { measurer.measure(it.label, TextStyle(fontFamily = Fonts.russo, fontSize = 30.sp)) }
    }
    val bond = remember(bondLine) {
        bondLine?.let { measurer.measure(it.uppercase(), TextStyle(fontFamily = Fonts.oswald, fontSize = 18.sp)) }
    }

    Spacer(modifier.drawWithCache {
        val cardW = 360.dp.toPx()
        val cardH = 202.dp.toPx()
        val border = 7.dp.toPx()
        val shadow = polygon(
            Offset(22.dp.toPx(), 26.dp.toPx()),
            Offset(cardW + 34.dp.toPx(), 8.dp.toPx()),
            Offset(cardW + 18.dp.toPx(), cardH + 30.dp.toPx()),
            Offset(2.dp.toPx(), cardH + 38.dp.toPx()),
        )
        val platePad = 18.dp.toPx()
        val plateH = (plate?.size?.height ?: 0) + 10.dp.toPx() + (bond?.size?.height ?: 0)
        val plateW = maxOf(plate?.size?.width ?: 0, bond?.size?.width ?: 0) + platePad * 2
        val plateTop = cardH + 26.dp.toPx()
        val plateLeft = cardW * 0.22f
        val plateShape = polygon(
            Offset(plateLeft + plateH * 0.3f, plateTop),
            Offset(plateLeft + plateW + plateH * 0.3f, plateTop),
            Offset(plateLeft + plateW, plateTop + plateH),
            Offset(plateLeft, plateTop + plateH),
        )
        val pivot = Offset(cardW / 2, cardH / 2)
        val iconSize = 120.dp.toPx()

        onDrawBehind {
            if (entry == null) return@onDrawBehind
            val p = progress(time.doubleValue, selectedAt, ENTER_SECONDS)
            val e = easeOutBack(p, 1.4f)
            translate(mix(-200.dp.toPx(), 0f, e), 0f) {
                rotate(mix(-18f, -6f, e), pivot) {
                    drawPath(shadow, palette.ink)
                    drawRect(palette.paper, size = Size(cardW, cardH))
                    clipRect(border, border, cardW - border, cardH - border) {
                        val art = (entry as? AppEntry)?.banner
                        if (art != null) {
                            drawDrawable(art, border, border, cardW - border, cardH - border)
                        } else {
                            drawRect(palette.ink)
                            val icon = (entry as? AppEntry)?.icon
                            if (icon != null) {
                                drawDrawable(icon, pivot.x - iconSize / 2, pivot.y - iconSize / 2, pivot.x + iconSize / 2, pivot.y + iconSize / 2)
                            } else if (shortcutIcon != null) {
                                translate(pivot.x - iconSize / 2, pivot.y - iconSize / 2) {
                                    with(shortcutIcon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(palette.paper)) }
                                }
                            }
                        }
                    }
                    val flash = (1f - p).pow(3)
                    if (flash > 0.01f) drawRect(palette.paper, size = Size(cardW, cardH), alpha = flash)
                    if (plate != null) {
                        drawPath(plateShape, palette.ink)
                        drawText(plate, color = palette.paper, topLeft = Offset(plateLeft + platePad, plateTop + 5.dp.toPx()))
                        if (bond != null) {
                            drawText(bond, color = palette.accent, topLeft = Offset(plateLeft + platePad, plateTop + 5.dp.toPx() + plate.size.height))
                        }
                    }
                }
            }
        }
    })
}
