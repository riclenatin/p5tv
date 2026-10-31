package com.chroniac.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.R
import com.chroniac.tvhome.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.format.TextStyle as DateTextStyle

/** Current time, refreshed on every minute boundary. */
@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return now
}

@Composable
private fun partOfDay(now: LocalDateTime): String = stringResource(
    when {
        now.hour in 5..11 -> R.string.part_morning
        now.hour in 12..16 -> R.string.part_day
        now.hour in 17..21 -> R.string.part_evening
        else -> R.string.part_night
    }
).uppercase()

private fun LocalDateTime.weekday(): String =
    dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()).uppercase()

/** Persona-style calendar (Collage theme): big day/month, weekday tag, part of day, time, weather. */
@Composable
fun DateBadge(weather: Weather?, palette: Palette, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Column(modifier.rotate(-7f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedText("%02d".format(now.dayOfMonth), 78.sp, palette)
            OutlinedText("/", 44.sp, palette, Modifier.padding(bottom = 10.dp))
            OutlinedText("%02d".format(now.monthValue), 44.sp, palette, Modifier.padding(bottom = 10.dp))
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .padding(bottom = 22.dp)
                    .rotate(9f)
                    .background(palette.paper, SkewShape(0.3f))
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                BasicText(now.weekday(), style = TextStyle(fontFamily = Fonts.russo, fontSize = 28.sp, color = palette.ink))
            }
        }
        Row(Modifier.offset(x = 22.dp, y = (-4).dp), verticalAlignment = Alignment.CenterVertically) {
            PartOfDayChip(partOfDay(now), palette)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(palette.paper, SkewShape(0.3f)).padding(horizontal = 18.dp, vertical = 2.dp)) {
                BasicText(
                    "%02d:%02d".format(now.hour, now.minute),
                    style = TextStyle(fontFamily = Fonts.dela, fontSize = 22.sp, color = palette.ink),
                )
            }
            if (weather != null) {
                Spacer(Modifier.width(8.dp))
                WeatherChip(weather, palette)
            }
        }
    }
}

/** Phantom layout, where the game shows the money: the time in the game's money digits. */
@Composable
fun PhantomClock(palette: Palette, modifier: Modifier = Modifier) {
    val now = rememberNow()
    MoneyDigits("%02d:%02d".format(now.hour, now.minute), palette, modifier.rotate(-3f))
}

/** Phantom layout, top-left corner (empty in the game): date, part of day and weather chips. */
@Composable
fun PhantomChips(weather: Weather?, palette: Palette, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Row(modifier.rotate(-4f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(palette.paper, SkewShape(0.3f)).padding(horizontal = 14.dp, vertical = 1.dp)) {
            BasicText(
                "%02d.%02d %s".format(now.dayOfMonth, now.monthValue, now.weekday()),
                style = TextStyle(fontFamily = Fonts.russo, fontSize = 18.sp, color = palette.ink),
            )
        }
        Spacer(Modifier.width(6.dp))
        PartOfDayChip(partOfDay(now), palette)
        if (weather != null) {
            Spacer(Modifier.width(6.dp))
            WeatherChip(weather, palette)
        }
    }
}

@Composable
private fun PartOfDayChip(text: String, palette: Palette) {
    Box(Modifier.background(palette.ink, SkewShape(0.3f)).padding(horizontal = 18.dp, vertical = 3.dp)) {
        BasicText(text, style = TextStyle(fontFamily = Fonts.russo, fontSize = 20.sp, color = palette.accent))
    }
}

@Composable
private fun WeatherChip(weather: Weather, palette: Palette) {
    Row(
        Modifier.background(palette.ink, SkewShape(0.3f)).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(weather, palette.paper, palette.accent, palette.ink, Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        BasicText(
            (if (weather.temperature > 0) "+" else "") + "${weather.temperature}°",
            style = TextStyle(fontFamily = Fonts.dela, fontSize = 20.sp, color = palette.paper),
        )
    }
}

/** White display text with a heavy black outline, readable over any backdrop. */
@Composable
private fun OutlinedText(text: String, size: TextUnit, palette: Palette, modifier: Modifier = Modifier) {
    val base = TextStyle(fontFamily = Fonts.dela, fontSize = size)
    Box(modifier) {
        BasicText(text, style = base.copy(color = palette.ink, drawStyle = Stroke(width = 14f, join = StrokeJoin.Round)))
        BasicText(text, style = base.copy(color = palette.paper))
    }
}

/** Flat icons in the P5 calendar spirit; WMO codes grouped the way Open-Meteo documents them. */
@Composable
private fun WeatherIcon(weather: Weather, color: Color, accent: Color, background: Color, modifier: Modifier) {
    Box(modifier.drawBehind {
        when (weather.code) {
            0, 1 -> if (weather.isDay) sun(accent) else moon(color, background)
            2 -> { sun(accent, Offset(-0.18f, -0.18f), 0.7f); cloud(color) }
            3 -> cloud(color)
            45, 48 -> fog(color)
            in 51..67, in 80..82 -> { cloud(color, lift = 0.18f); rain(color) }
            in 71..77, 85, 86 -> { cloud(color, lift = 0.18f); snow(color) }
            in 95..99 -> { cloud(color, lift = 0.18f); bolt(accent) }
            else -> cloud(color)
        }
    })
}

private fun DrawScope.sun(color: Color, shift: Offset = Offset.Zero, scale: Float = 1f) {
    val c = center + Offset(shift.x * size.width, shift.y * size.height)
    val r = size.minDimension * 0.22f * scale
    drawCircle(color, r, c)
    val rayWidth = size.minDimension * 0.08f
    for (k in 0 until 8) {
        val a = (k * PI / 4).toFloat()
        val d = Offset(cos(a), sin(a))
        drawLine(color, c + d * (r * 1.45f), c + d * (r * 2.1f), rayWidth, StrokeCap.Round)
    }
}

/** Crescent: a disc with an offset disc in the plate colour bitten out of it. */
private fun DrawScope.moon(color: Color, background: Color) {
    val r = size.minDimension * 0.36f
    drawCircle(color, r, center)
    drawCircle(background, r * 0.86f, center + Offset(r * 0.45f, -r * 0.3f))
}

private fun DrawScope.cloud(color: Color, lift: Float = 0f) {
    val u = size.minDimension
    val base = center + Offset(0f, -lift * u + u * 0.08f)
    drawCircle(color, u * 0.20f, base + Offset(-u * 0.18f, u * 0.04f))
    drawCircle(color, u * 0.26f, base + Offset(u * 0.02f, -u * 0.06f))
    drawCircle(color, u * 0.18f, base + Offset(u * 0.24f, u * 0.06f))
    drawRect(color, topLeft = base + Offset(-u * 0.36f, u * 0.04f), size = androidx.compose.ui.geometry.Size(u * 0.78f, u * 0.2f))
}

private fun DrawScope.rain(color: Color) {
    val u = size.minDimension
    for (k in -1..1) {
        val x = center.x + k * u * 0.24f
        drawLine(color, Offset(x + u * 0.06f, center.y + u * 0.24f), Offset(x - u * 0.04f, center.y + u * 0.46f), u * 0.07f, StrokeCap.Round)
    }
}

private fun DrawScope.snow(color: Color) {
    val u = size.minDimension
    for (k in -1..1) drawCircle(color, u * 0.06f, Offset(center.x + k * u * 0.24f, center.y + u * 0.36f + (k % 2) * u * 0.06f))
}

private fun DrawScope.bolt(color: Color) {
    val u = size.minDimension
    drawPath(
        polygon(
            Offset(center.x + u * 0.06f, center.y + u * 0.12f),
            Offset(center.x - u * 0.14f, center.y + u * 0.36f),
            Offset(center.x + u * 0.02f, center.y + u * 0.34f),
            Offset(center.x - u * 0.08f, center.y + u * 0.52f),
        ),
        color,
    )
}

private fun DrawScope.fog(color: Color) {
    val u = size.minDimension
    for (k in 0..2) {
        val y = center.y - u * 0.22f + k * u * 0.22f
        drawLine(color, Offset(center.x - u * 0.38f + k * u * 0.06f, y), Offset(center.x + u * 0.38f - k * u * 0.04f, y), u * 0.09f, StrokeCap.Round)
    }
}
