package com.chroniac.tvhome.ui

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.chroniac.tvhome.R
import kotlin.math.roundToInt

@Immutable
data class Palette(
    /** Signature colour (Phantom red). */
    val accent: Color,
    /** Main backdrop fill. */
    val backdrop: Color,
    /** Second backdrop tone for stripes and rays. */
    val deep: Color,
    val ink: Color,
    val paper: Color,
    /** Shadow block behind the selected command (P5's cyan). */
    val highlight: Color,
)

val PhantomPalette = Palette(
    accent = Color(0xFFE3001B),
    backdrop = Color(0xFFE3001B),
    deep = Color(0xFF7A0010),
    ink = Color(0xFF000000),
    paper = Color(0xFFFFFFFF),
    highlight = Color(0xFF30FFF0),
)

object Fonts {
    /** Heavy square display face for menu words and numbers. */
    val dela = FontFamily(Font(R.font.dela_gothic_one))
    /** Wide techno face for labels; mixed into the ransom-note lettering. */
    val russo = FontFamily(Font(R.font.russo_one))
    /** Condensed face for hints and the third ransom-note style. */
    val oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
}

// Offset is a value class, which Kotlin forbids as a vararg type; every shape here has 3 or 4 corners.
fun polygon(a: Offset, b: Offset, c: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

fun polygon(a: Offset, b: Offset, c: Offset, d: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

/** Parallelogram leaning right; [lean] is the horizontal shift as a fraction of the height. */
class SkewShape(private val lean: Float = 0.25f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val shift = size.height * lean
        return Outline.Generic(
            polygon(
                Offset(shift, 0f),
                Offset(size.width, 0f),
                Offset(size.width - shift, size.height),
                Offset(0f, size.height),
            )
        )
    }
}

/** Draws a platform Drawable (app banners and icons) into the current transform. */
fun DrawScope.drawDrawable(drawable: Drawable, left: Float, top: Float, right: Float, bottom: Float) {
    drawIntoCanvas { canvas ->
        drawable.setBounds(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
        drawable.draw(canvas.nativeCanvas)
    }
}
