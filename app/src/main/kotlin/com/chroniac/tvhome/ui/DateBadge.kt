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
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chroniac.tvhome.R
import com.chroniac.tvhome.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as DateTextStyle

/** Current time, refreshed on every minute boundary. */
@Composable
internal fun rememberNow(): LocalDateTime {
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
fun DateBadge(weather: Weather?, palette: Palette, time: DoubleState, modifier: Modifier = Modifier) {
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
                WeatherChip(weather, palette, time)
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

@Composable
private fun PartOfDayChip(text: String, palette: Palette) {
    Box(Modifier.background(palette.ink, SkewShape(0.3f)).padding(horizontal = 18.dp, vertical = 3.dp)) {
        BasicText(text, style = TextStyle(fontFamily = Fonts.russo, fontSize = 20.sp, color = palette.accent))
    }
}

/** The game's weather icon on its plate, overlapping a black tag with the temperature. */
@Composable
private fun WeatherChip(weather: Weather, palette: Palette, time: DoubleState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(GameWeather.of(weather.code), palette, time, Modifier.zIndex(1f))
        Box(
            Modifier
                .offset(x = (-10).dp)
                .background(palette.ink, SkewShape(0.3f))
                .padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
        ) {
            BasicText(
                (if (weather.temperature > 0) "+" else "") + "${weather.temperature}°",
                style = TextStyle(fontFamily = Fonts.dela, fontSize = 20.sp, color = palette.paper),
            )
        }
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

/**
 * The weather icon of the game's field date HUD (sprites from tools/p5r/make_assets.py). As in the
 * game, the icon is cut out of its tilted paper plate, so whatever is behind shows through it, and
 * it loops through its three frames; both are drawn at one scale, the icon centred on the plate.
 */
@Composable
private fun WeatherIcon(weather: GameWeather, palette: Palette, time: DoubleState, modifier: Modifier = Modifier) {
    val plate = ImageBitmap.imageResource(R.drawable.p5_weather_plate)
    val frames = weather.frames.map { ImageBitmap.imageResource(it) }
    val paper = remember(palette) { ColorFilter.tint(palette.paper) }
    Spacer(
        modifier
            .size(WEATHER_PLATE * plate.width / plate.height, WEATHER_PLATE)
            // Its own layer, so the cut-out clears only the plate.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val s = size.height / plate.height
                drawImage(plate, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), colorFilter = paper)
                val icon = frames[(time.doubleValue / WEATHER_FRAME_SECONDS).toInt().mod(frames.size)]
                val w = (icon.width * s).roundToInt()
                val h = (icon.height * s).roundToInt()
                val offset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
                drawImage(icon, dstOffset = offset, dstSize = IntSize(w, h), blendMode = BlendMode.DstOut)
            }
    )
}

/** Height of the weather plate; the sprites are pre-scaled for it on a 1080p screen (80 px). */
private val WEATHER_PLATE = 40.dp

/**
 * Each icon frame is held 18 game frames (0.3 s at 60 fps), looping 1-2-3, as measured on 60 fps
 * footage of the game's HUD.
 */
internal const val WEATHER_FRAME_SECONDS = 0.3

/** The game's four kinds of weather, each a looping three-frame icon; [asset] names its sprites. */
internal enum class GameWeather(val asset: String, val frames: IntArray) {
    Sunny("sunny", intArrayOf(R.drawable.p5_weather_sunny_1, R.drawable.p5_weather_sunny_2, R.drawable.p5_weather_sunny_3)),
    Cloudy("cloudy", intArrayOf(R.drawable.p5_weather_cloudy_1, R.drawable.p5_weather_cloudy_2, R.drawable.p5_weather_cloudy_3)),
    Rain("rain", intArrayOf(R.drawable.p5_weather_rain_1, R.drawable.p5_weather_rain_2, R.drawable.p5_weather_rain_3)),
    Snow("snow", intArrayOf(R.drawable.p5_weather_snow_1, R.drawable.p5_weather_snow_2, R.drawable.p5_weather_snow_3)),
    ;

    companion object {
        /**
         * The game's weather for a WMO code as Open-Meteo reports it. Partly cloudy still counts as
         * sunny, as in Japanese forecasts (晴れ covers skies up to 80 % clouded) and only overcast is
         * cloudy; fog counts as cloudy, drizzle and thunderstorms as rain.
         */
        fun of(code: Int) = when (code) {
            0, 1, 2 -> Sunny
            in 51..67, in 80..82, in 95..99 -> Rain
            in 71..77, 85, 86 -> Snow
            else -> Cloudy
        }
    }
}
