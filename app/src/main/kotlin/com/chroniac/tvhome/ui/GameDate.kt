package com.chroniac.tvhome.ui

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.roundToInt

/*
 * Persona 5 Royal's field date display, the calendar in the top-left corner while walking around,
 * rebuilt from the game's own sprites (assets/p5_date, from tools/p5r/make_assets.py). Every
 * sprite is drawn at the game's scale by its centre and rotation as measured on 60 fps footage of
 * the game; coordinates are px of a 1920x1080 frame, where the game puts the display too.
 *
 * Layers combine the way the game's frames show: white parts are opaque, and black parts replace
 * whatever is under them with black at 75 % opacity, so the scene shows through every black shape,
 * letters and weather icon included. The order is the game's: all white bases, then the black
 * bases (and the icon and time-of-day text), then the numerals and the weekday outline, then the
 * weekday letters, and the special-weather banner on top.
 */

/**
 * The date display with the time of day, the weather and, when the weather calls for it, the game's
 * special-weather banner; the temperature sits on a black tag tucked under the weather plate.
 */
@Composable
fun GameDate(weather: Weather?, palette: Palette, time: DoubleState, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val slot = timeSlot(now)
    val face = DateFace(
        month = now.monthValue,
        day = now.dayOfMonth,
        weekday = now.dayOfWeek.value % 7,
        slot = slot,
        weather = weather?.let { GameWeather.of(it.code) },
        banner = weather?.let { WeatherBanner.of(it, slot) },
    )
    val assets = LocalContext.current.assets
    // Rendered off the main thread; the previous face stays up until the new one is ready.
    val rendered by produceState<Rendered?>(null, face, palette) {
        withContext(Dispatchers.IO) { render(assets, face, palette) }?.let { value = it }
    }
    Box(modifier.fillMaxSize()) {
        rendered?.let { shown ->
            // The temperature tag goes first, so the weather plate overlaps its left end; its black is
            // the display's see-through black.
            if (weather != null && shown.face.weather != null) {
                Box(
                    Modifier
                        .atFramePx(shown.face.plate.x + TAG_FROM_PLATE_X, TAG_Y)
                        .rotate(-4f)
                        .background(palette.ink.copy(alpha = BLACK_ALPHA), SkewShape(0.3f))
                        .padding(start = 26.dp, end = 14.dp, top = 1.dp, bottom = 1.dp)
                ) {
                    BasicText(
                        (if (weather.temperature > 0) "+" else "") + "${weather.temperature}°",
                        style = TextStyle(fontFamily = Fonts.dela, fontSize = 20.sp, color = palette.paper),
                    )
                }
            }
            Spacer(Modifier.fillMaxSize().drawBehind {
                val k = size.height / REF_H
                val frame = shown.frames[(time.doubleValue / WEATHER_FRAME_SECONDS).toInt().mod(shown.frames.size)]
                scale(k, k, Offset.Zero) { drawImage(frame) }
            })
        }
    }
}

/** What the display shows; any change renders it again. */
private data class DateFace(
    val month: Int,
    val day: Int,
    /** 0 = Sunday, as the game orders its weekday sprites. */
    val weekday: Int,
    val slot: Int,
    val weather: GameWeather?,
    val banner: WeatherBanner?,
) {
    val tens get() = day / 10
    val units get() = day % 10

    /** A one-digit day has its own layout, with the weather plate further left. */
    val plate get() = if (tens == 0) PLATE_ONE_DIGIT else PLATE

    /** Asset names of every sprite this face needs. */
    fun parts(): List<String> = buildList {
        for (part in DIGIT_PARTS) {
            add("month_${month}_$part")
            if (tens > 0) add("digit_${tens}_$part")
            add("digit_${units}_$part")
        }
        for (part in WEEKDAY_PARTS) add("weekday_${weekday}_$part")
        add("time_${slot}_base")
        add("time_${slot}_text")
        if (weather != null) {
            add("weather_plate")
            for (n in 1..ICON_FRAMES) add("weather_${weather.asset}_$n")
        }
        if (banner != null) add("banner_${banner.asset}")
    }
}

/** The finished display at 1080p: one picture per weather-icon frame, so each frame costs one bitmap. */
private class Rendered(val face: DateFace, val frames: List<ImageBitmap>)

private val DIGIT_PARTS = listOf("base", "black", "numeral")
private val WEEKDAY_PARTS = listOf("base", "black", "outline", "letters")

/** Loads the face's sprites and draws its frames; the sprites are freed once drawn. */
private fun render(assets: AssetManager, face: DateFace, palette: Palette): Rendered? {
    val sprites = try {
        face.parts().associateWith { name ->
            assets.open("p5_date/$name.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }
    } catch (_: FileNotFoundException) {
        return null // assets not extracted from the game: no date display
    }
    val frames = List(if (face.weather != null) ICON_FRAMES else 1) { icon ->
        ImageBitmap(HUD_W, HUD_H, ImageBitmapConfig.Argb8888).also { bitmap ->
            val size = Size(HUD_W.toFloat(), HUD_H.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
                drawFace(face, sprites, palette, icon)
            }
            bitmap.prepareToDraw()
        }
    }
    sprites.values.forEach { it.asAndroidBitmap().recycle() }
    return Rendered(face, frames)
}

private fun DrawScope.drawFace(face: DateFace, sprites: Map<String, ImageBitmap>, palette: Palette, icon: Int) {
    val paper = ColorFilter.tint(palette.paper)
    val ink = ColorFilter.tint(palette.ink)
    val outline = ColorFilter.tint(
        when (face.weekday) {
            0 -> SUNDAY_RED
            6 -> SATURDAY_CYAN
            else -> palette.paper
        }
    )
    fun sprite(name: String) = sprites.getValue(name)
    val weekday = WEEKDAYS[face.weekday]
    val slot = TIME_SLOTS[face.slot]
    // A one-digit day has its own layout: the month moves right and the weather plate left.
    val oneDigit = face.tens == 0
    val plate = face.plate
    val digits = buildList {
        if (oneDigit) {
            add("month_${face.month}" to MONTH_ONE_DIGIT)
            add("digit_${face.units}" to ONE_DIGIT)
        } else {
            add("month_${face.month}" to MONTH)
            add("digit_${face.tens}" to TENS)
            add("digit_${face.units}" to UNITS[face.units])
        }
    }

    // White bases.
    if (face.weather != null) place(sprite("weather_plate"), plate, paper)
    for ((name, at) in digits) place(sprite("${name}_base"), at, paper)
    place(sprite("weekday_${face.weekday}_base"), weekday, paper)
    place(sprite("time_${face.slot}_base"), slot, paper)
    // Black bases, the weather icon and the time-of-day text.
    if (face.weather != null) replace(sprite("weather_${face.weather.asset}_${icon + 1}"), plate, ink)
    for ((name, at) in digits) replace(sprite("${name}_black"), at, ink)
    replace(sprite("weekday_${face.weekday}_black"), weekday, ink)
    replace(sprite("time_${face.slot}_text"), slot, ink)
    // Numerals and the weekday outline (red on Sundays, cyan on Saturdays), then the letters.
    for ((name, at) in digits) place(sprite("${name}_numeral"), at, paper)
    place(sprite("weekday_${face.weekday}_outline"), weekday, outline)
    replace(sprite("weekday_${face.weekday}_letters"), weekday, ink)
    // The banner is coloured art, drawn as it is; banners start at the same left edge.
    if (face.banner != null) {
        val banner = sprite("banner_${face.banner.asset}")
        place(banner, Place(BANNER_LEFT + banner.width / 2f, BANNER_Y), null)
    }
}

/** Draws [image] centred on [at], tinted with [color] (or as it is). */
private fun DrawScope.place(image: ImageBitmap, at: Place, color: ColorFilter?) = transformed(at) {
    drawImage(image, image.centred(), colorFilter = color)
}

/** Replaces what is under [image]'s shape with [color] at the game's black opacity. */
private fun DrawScope.replace(image: ImageBitmap, at: Place, color: ColorFilter) = transformed(at) {
    drawImage(image, image.centred(), blendMode = BlendMode.DstOut)
    drawImage(image, image.centred(), alpha = BLACK_ALPHA, colorFilter = color)
}

private inline fun DrawScope.transformed(at: Place, block: DrawScope.() -> Unit) = withTransform({
    translate(at.x, at.y)
    rotate(at.degrees, Offset.Zero)
}, block)

private fun ImageBitmap.centred() = Offset(-width / 2f, -height / 2f)

/** A sprite's centre in the 1080p frame and its rotation (degrees, clockwise). */
private class Place(val x: Float, val y: Float, val degrees: Float = 0f)

/** Puts the element's top-left at ([x], [y]) px of the 1080p frame, scaled to the screen. */
private fun Modifier.atFramePx(x: Float, y: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val k = constraints.maxHeight / REF_H
    layout(placeable.width, placeable.height) { placeable.place((x * k).roundToInt(), (y * k).roundToInt()) }
}

/** The temperature tag's top-left: right of the weather plate's centre, near the frame's top. */
private const val TAG_FROM_PLATE_X = 46.5f
private const val TAG_Y = 44f

private const val REF_H = 1080f

/** The area the display (with a banner) can cover, in px from the 1080p frame's top-left corner. */
private const val HUD_W = 540
private const val HUD_H = 360

private const val BLACK_ALPHA = 0.75f
private val SUNDAY_RED = Color(0xFFF00004)
private val SATURDAY_CYAN = Color(0xFF03FCE8)

private const val ICON_FRAMES = 3

private val PLATE = Place(361.5f, 66f)
private val MONTH = Place(99.5f, 111f)
private val TENS = Place(179f, 122.5f, -3.5f)

/** Units digit of a two-digit day; the game tilts each digit its own way. */
private val UNITS = arrayOf(
    Place(257f, 105.5f, 5f), Place(255f, 106.5f, 5f), Place(255.5f, 104.5f, 6f), Place(258f, 103.5f, 1f),
    Place(259f, 104.5f, -0.5f), Place(256f, 104f, 7.5f), Place(260f, 106.5f, -1f), Place(256.5f, 106f, 2f),
    Place(258f, 105.5f, 1.5f), Place(255.5f, 103f, 8f),
)

/** The layout of a one-digit day (measured on Persona 5 footage of 5/7, 6/7 and 7/9). */
private val MONTH_ONE_DIGIT = Place(129.5f, 111f)
private val ONE_DIGIT = Place(218f, 105.5f, 7f)
private val PLATE_ONE_DIGIT = Place(346.5f, 66f)

/** Weekday parts, Sunday first: the game places each word on its own. */
private val WEEKDAYS = arrayOf(
    Place(281f, 158.5f, -12f), Place(274.5f, 150f, -12f), Place(274.5f, 159.5f, -14f), Place(301.5f, 155.5f, -13.5f),
    Place(272.5f, 153.5f, -13.5f), Place(270.5f, 154.5f, -10.5f), Place(293.5f, 150.5f, -10f),
)

/**
 * Time-of-day plates in the game's slot order. The game right-aligns the words, so Late Night, not
 * seen in the footage, is placed where its width puts it against the others' common right end.
 */
private val TIME_SLOTS = arrayOf(
    Place(123.5f, 257f, -22.5f), Place(116f, 257f, -22.5f), Place(115.25f, 255.5f, -22.5f), Place(106.5f, 260f, -22.5f),
    Place(115.5f, 256f, -22.5f), Place(129f, 252.5f, -22.5f), Place(115.5f, 257.5f, -22.5f), Place(111.5f, 259f, -22.5f),
)

/** Special-weather banners: their left edge and centre height (the game left-aligns them). */
private const val BANNER_LEFT = 238.5f
private const val BANNER_Y = 217.5f

private const val EARLY_MORNING = 0
private const val MORNING = 1
private const val DAYTIME = 2
private const val LUNCHTIME = 3
private const val AFTERNOON = 4
private const val AFTER_SCHOOL = 5
private const val EVENING = 6
private const val LATE_NIGHT = 7

/**
 * The game's time slot for a clock time. Its school days run Early Morning, Morning, Lunchtime,
 * Afternoon, After School, Evening; Sundays have Daytime instead of the school hours.
 */
private fun timeSlot(now: LocalDateTime): Int {
    val hour = now.hour
    return when {
        hour in 5..7 -> EARLY_MORNING
        hour in 18..22 -> EVENING
        hour >= 23 || hour < 5 -> LATE_NIGHT
        now.dayOfWeek == DayOfWeek.SUNDAY -> DAYTIME
        hour < 12 -> MORNING
        hour < 13 -> LUNCHTIME
        hour < 16 -> AFTERNOON
        else -> AFTER_SCHOOL
    }
}

/** The game's special-weather banners that real weather data can trigger. */
private enum class WeatherBanner(val asset: String) {
    Typhoon("typhoon"),
    TorrentialRain("torrential_rain"),
    ColdWave("cold_wave"),
    HotNight("hot_night"),
    HeatWave("heat_wave"),
    ;

    companion object {
        /**
         * Typhoon for storm-force gusts (25 m/s); Torrential Rain (ゲリラ豪雨) for heavy rain,
         * violent showers and thunderstorms; Cold Wave (寒波) from -25 °C; at night Hot Night
         * (熱帯夜, a night that stays at 25 °C or more), by day Heat Wave (ヒートアイランド) from
         * 35 °C (猛暑日).
         */
        fun of(weather: Weather, slot: Int): WeatherBanner? {
            val night = slot == EVENING || slot == LATE_NIGHT
            return when {
                weather.gusts >= 25f -> Typhoon
                weather.code in TORRENTIAL -> TorrentialRain
                weather.temperature <= -25 -> ColdWave
                night && weather.temperature >= 25 -> HotNight
                !night && weather.temperature >= 35 -> HeatWave
                else -> null
            }
        }

        /** WMO codes: heavy rain, heavy freezing rain, violent rain showers, thunderstorms. */
        private val TORRENTIAL = setOf(65, 67, 82, 95, 96, 99)
    }
}
