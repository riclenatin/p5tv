package com.chroniac.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.Entry
import com.chroniac.tvhome.R
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Persona 5 Royal's camp ("command") menu, rebuilt from the game's own sprites (extracted from
 * the user's P5R install by tools/p5r/make_assets.py into res/drawable-nodpi, pre-scaled for a
 * 1080p frame). Positions, scales and rotations were measured on an in-game screenshot by template
 * matching the same sprites; every coordinate below is in pixels of a 1920x1080 frame, scaled to
 * the actual size. Menu words are generated ransom lettering because app names are not in the game.
 * Motion follows the game frame by frame (see Opening and CursorChange).
 *
 * The game draws all of its art at about 81% brightness (whites at 207, blacks untouched) and only
 * the HUD on top (the command list, money, captions) in full white; that is what makes the list
 * read so crisply, and ART reproduces it.
 */

private const val REF_H = 1080f

/** Multiplier for every art layer under the HUD: white becomes 207, as in the game. */
private val ART = Color(0xFFCFCFCF)

/** The blurred room behind the menu, which the game shows as a dark, nearly flat red. */
private val ROOM = Color(0xFF5B0F07)
private val ROOM_DEEP = Color(0xFF4F0C03)
private val ROOM_LIGHT = Color(0xFF7D160F)

/** The selected command's letters and the cursor's red (its cyan is the palette highlight). */
private val MINT = Color(0xFFE7FFDD)
private val CURSOR_RED = Color(0xFFE60026)

/** The plate above "MENU: Main" shows the player's team name; this is the one in the reference. */
private const val TEAM_NAME = "The Phantoms"

/** One frame of an [Opening] shake: an offset, plus a zoom about the anchor (frame px times [k]). */
private inline fun DrawScope.shaken(shake: FloatArray?, k: Float, anchorX: Float, anchorY: Float, block: DrawScope.() -> Unit) {
    if (shake == null) {
        block()
        return
    }
    withTransform({
        translate(shake[0] * k, shake[1] * k)
        if (shake.size > 2) scale(shake[2], shake[2], Offset(anchorX * k, anchorY * k))
    }, block)
}

/**
 * Static layers (room, coat shadow, star panel), prerendered once per size; they shake on opening.
 * While the silhouettes play, the panel reaches further left behind an edge that swings round
 * ([Opening.panelEdgeSlope]), so it is a second, wider star layer clipped per frame.
 */
@Composable
fun PhantomBackdrop(palette: Palette, state: MenuState, time: DoubleState, modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val frame = size
        val rest = prerender(size.width.roundToInt(), size.height.roundToInt(), ImageBitmapConfig.Rgb565) {
            drawCampBackdrop(palette)
        }
        val starsLeft = OPENING_STARS_LEFT * k
        val stars = prerender((size.width - starsLeft).roundToInt(), size.height.roundToInt(), ImageBitmapConfig.Rgb565) {
            translate(-starsLeft, 0f) { drawStarField(palette.ink, frame, opening = true) }
        }
        val edges = List(Opening.ART) { n ->
            val top = Opening.panelEdgeTop(n) * k
            val bottom = top + Opening.panelEdgeSlope(n) * size.height
            polygon(Offset(top, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(bottom, size.height))
        }
        onDrawBehind {
            val n = gameFrame(time.doubleValue, state.openedAt)
            if (n < Opening.ART) {
                drawRoom()
                clipPath(edges[n.coerceAtLeast(0)]) { drawImage(stars, topLeft = Offset(starsLeft, 0f)) }
            } else {
                shaken(Opening.backgroundShake(n), k, 225f, 540f) { drawImage(rest) }
            }
        }
    })
}

/** Left end of the opening's star layer: the panel edge never comes further left (frame px). */
private const val OPENING_STARS_LEFT = 800f

/** Out-of-focus room behind the menu: a darker wall on the left and two soft, lighter patches. */
private fun DrawScope.drawRoom() {
    val w = size.width
    val h = size.height
    drawRect(ROOM)
    drawPath(polygon(Offset(0f, 0f), Offset(w * 0.12f, 0f), Offset(w * 0.08f, h), Offset(0f, h)), ROOM_DEEP)
    drawCircle(ROOM_LIGHT, h * 0.15f, Offset(w * 0.13f, h * 0.17f), alpha = 0.6f)
    drawCircle(ROOM_LIGHT, h * 0.24f, Offset(w * 0.17f, h * 0.92f), alpha = 0.35f)
}

private fun DrawScope.drawCampBackdrop(p: Palette) {
    val w = size.width
    val h = size.height
    drawRoom()
    // Black behind Joker's coat, then the black-and-white star panel on the right.
    drawPath(polygon(Offset(w * 0.55f, h), Offset(w * 0.66f, 0f), Offset(w, 0f), Offset(w, h)), p.ink)
    val panel = polygon(Offset(w * 0.677f, 0f), Offset(w, 0f), Offset(w, h), Offset(w * 0.755f, h))
    clipPath(panel) { drawStarField(p.ink, size) }
    drawPath(panel, p.ink, style = Stroke(h * 0.006f))
}

/**
 * The camp background's starbursts in a [frame]-sized layout: big stars in concentric black and
 * white bands, overlapping until no plain paper is left, and small solid stars on top. The
 * [opening] panel gets more bursts underneath, where it reaches further left than the resting one.
 */
private fun DrawScope.drawStarField(ink: Color, frame: Size, opening: Boolean = false) {
    val w = frame.width
    val h = frame.height
    drawRect(ink, Offset.Zero, frame)
    fun burst(random: Random, left: Float, span: Float) {
        val c = Offset(w * (left + random.nextFloat() * span), h * (random.nextFloat() * 1.15f - 0.08f))
        val rot = random.nextFloat() * 72f
        val band = h * (0.016f + random.nextFloat() * 0.01f)
        var r = h * (0.14f + random.nextFloat() * 0.16f)
        var k = 0
        while (r > band) {
            drawPath(star(c, r, r * 0.48f, rot), if (k++ % 2 == 0) ink else ART)
            r -= band
        }
    }
    if (opening) {
        val extra = Random(23)
        repeat(8) { burst(extra, 0.43f, 0.26f) }
    }
    val random = Random(11) // fixed seed: the same wallpaper every time
    repeat(16) { burst(random, 0.66f, 0.36f) }
    repeat(10) {
        val c = Offset(w * (0.68f + random.nextFloat() * 0.32f), h * random.nextFloat())
        val r = h * (0.025f + random.nextFloat() * 0.035f)
        val rot = random.nextFloat() * 72f
        drawPath(star(c, r * 1.35f, r * 0.65f, rot), ink)
        drawPath(star(c, r, r * 0.45f, rot), ART)
    }
}

/** Joker, hand over his face, at the art brightness. It cuts in on the opening's art frame. */
@Composable
fun CampHero(state: MenuState, time: DoubleState, modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.p5_camptop)
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val dst = IntSize((art.width * k).roundToInt(), (art.height * k).roundToInt())
        val origin = IntOffset((HERO_X * k).roundToInt(), (HERO_Y * k).roundToInt())
        val dim = ColorFilter.tint(ART, BlendMode.Modulate)
        onDrawBehind {
            val n = gameFrame(time.doubleValue, state.openedAt)
            if (n < Opening.ART) return@onDrawBehind
            shaken(Opening.artShake(n), k, 865f, 107f) { drawImage(art, dstOffset = origin, dstSize = dst, colorFilter = dim) }
        }
    })
}

/** Top-left of the art in the frame (the asset is already at the game's scale). */
private const val HERO_X = 580f
private const val HERO_Y = -14f

/**
 * The opening's flipbook: Joker's 3D menu model, rendered from the game files by tools/p5r/opening
 * ([OpeningFrames]), white left of the star panel's edge and black over it, one image per frame
 * until the art cuts in. On the hand-thrust frame the game also draws streaks the model lacks.
 */
@Composable
fun OpeningSilhouettes(state: MenuState, time: DoubleState, palette: Palette, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.assets
    val opening by produceState(OpeningFrames.NONE, assets) { value = withContext(Dispatchers.IO) { OpeningFrames.load(assets) } }
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val white = ColorFilter.tint(ART)
        val black = ColorFilter.tint(palette.ink)
        val streaks = SMEAR_STREAKS.map { s -> polygon(Offset(s[0] * k, s[1] * k), Offset(s[2] * k, s[3] * k), Offset(s[4] * k, s[5] * k), Offset(s[6] * k, s[7] * k)) }
        onDrawBehind {
            val n = gameFrame(time.doubleValue, state.openedAt)
            val frame = opening.frames.getOrNull(n) ?: return@onDrawBehind
            val stretch = opening.scale * k
            fun draw(mask: OpeningFrames.Mask?, tint: ColorFilter) {
                if (mask == null) return
                drawImage(
                    mask.image,
                    dstOffset = IntOffset((mask.x * k).roundToInt(), (mask.y * k).roundToInt()),
                    dstSize = IntSize((mask.image.width * stretch).roundToInt(), (mask.image.height * stretch).roundToInt()),
                    colorFilter = tint,
                )
            }
            draw(frame.black, black)
            draw(frame.white, white)
            if (n == Opening.SMEAR) for (s in streaks) drawPath(s, palette.ink)
        }
    })
}

/** Smear frame: streaks shooting up from Joker's head over the plates (four quads, frame px). */
private val SMEAR_STREAKS = arrayOf(
    floatArrayOf(1110f, 330f, 1140f, 320f, 1260f, 0f, 1215f, 0f),
    floatArrayOf(1180f, 330f, 1215f, 320f, 1420f, 0f, 1370f, 0f),
    floatArrayOf(1250f, 340f, 1290f, 330f, 1560f, 0f, 1500f, 0f),
    floatArrayOf(1320f, 360f, 1350f, 352f, 1700f, 40f, 1650f, 30f),
)

/**
 * The fixed stickers and plates at the centres and rotations (degrees, clockwise) measured in the
 * game: "Dont Look at", "LIKE THAT!", the team-name plate and the black "MENU: Main / Select a
 * Command" plate. They fade in with the panel and jump during the opening's shake.
 */
@Composable
fun CampStickers(palette: Palette, state: MenuState, time: DoubleState, modifier: Modifier = Modifier) {
    val dontLook = ImageBitmap.imageResource(R.drawable.p5_dont_look)
    val likeThat = ImageBitmap.imageResource(R.drawable.p5_like_that)
    val menu = ImageBitmap.imageResource(R.drawable.p5_menu)
    val main = ImageBitmap.imageResource(R.drawable.p5_menu_main)
    val select = ImageBitmap.imageResource(R.drawable.p5_select_command)
    val measurer = rememberTextMeasurer()
    val team = remember(measurer, palette) {
        measurer.measure(TEAM_NAME, TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = palette.ink))
    }
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val ink = ColorFilter.tint(palette.ink)
        val art = ColorFilter.tint(ART)
        onDrawBehind {
            val n = gameFrame(time.doubleValue, state.openedAt)
            val a = Opening.stickerAlpha(n)
            scale(k, k, Offset.Zero) {
                shaken(Opening.stickerShake(n), 1f, 0f, 0f) {
                    // "Dont Look at": light letters on a black strip, rising to the right, mostly under LIKE THAT.
                    at(1440f, 69f, -13f) {
                        drawRect(palette.ink, Offset(-dontLook.width / 2f - 14f, -dontLook.height / 2f - 6f), Size(dontLook.width + 28f, dontLook.height + 12f), alpha = a)
                        drawImage(dontLook, topLeft = centeredOn(dontLook), alpha = a, colorFilter = art)
                    }
                    at(1628f, 88f, -2f) {
                        sticker(363f, 105f, palette.ink, a)
                        drawImage(likeThat, topLeft = centeredOn(likeThat) + Offset(5f, 5f), alpha = a, colorFilter = ink)
                    }
                    at(1675f, 281f, 6f) {
                        sticker(350f, 74f, palette.ink, a)
                        drawText(team, topLeft = Offset(-team.size.width / 2f, -team.size.height / 2f), alpha = a)
                    }
                }
                shaken(Opening.plateShake(n), 1f, 0f, 0f) {
                    at(1715f, 400f, 12f) { drawRect(palette.ink, Offset(-200f, -86f), Size(400f, 172f), alpha = a) }
                    at(1654f, 380f, 12f) { drawImage(menu, topLeft = centeredOn(menu), alpha = a, colorFilter = art) }
                    at(1816f, 404f, 12f) { drawImage(main, topLeft = centeredOn(main), alpha = a, colorFilter = art) }
                    at(1708f, 446f, 12f) { drawImage(select, topLeft = centeredOn(select), alpha = a, colorFilter = art) }
                }
            }
        }
    })
}

/**
 * "COMMAND", drawn with a difference blend as in the game: black where it crosses the white hand
 * (the hand's black outline turns white inside the "C"), light over the black coat. It fades in
 * just before the art and never shakes.
 */
@Composable
fun CommandTitle(state: MenuState, time: DoubleState, modifier: Modifier = Modifier) {
    val command = ImageBitmap.imageResource(R.drawable.p5_command)
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val art = ColorFilter.tint(ART)
        onDrawBehind {
            val a = Opening.commandAlpha(gameFrame(time.doubleValue, state.openedAt))
            if (a > 0f) scale(k, k, Offset.Zero) {
                drawImage(command, topLeft = Offset(804f, 702f), alpha = a, colorFilter = art, blendMode = BlendMode.Difference)
            }
        }
    })
}

/** Runs [block] with the origin at ([x], [y]) in the 1080p frame, rotated [degrees] clockwise. */
private inline fun DrawScope.at(x: Float, y: Float, degrees: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(x, y)
        rotate(degrees, Offset.Zero)
    }, block)
}

private fun centeredOn(image: ImageBitmap) = Offset(-image.width / 2f, -image.height / 2f)

/** A light sticker of [w] x [h] centred on the origin, with a black border. */
private fun DrawScope.sticker(w: Float, h: Float, ink: Color, alpha: Float) {
    val topLeft = Offset(-w / 2, -h / 2)
    drawRect(ART, topLeft, Size(w, h), alpha = alpha)
    drawRect(ink, topLeft, Size(w, h), alpha = alpha, style = Stroke(5f))
}

private const val MAX_VISIBLE = 9

/** The game's nine row slots: first centre, spacing, and each row's ragged right edge. */
private const val ROW_TOP = 277f
private const val ROW_STEP = 61.3f
private val ROW_RIGHT = floatArrayOf(1060f, 1054f, 1024f, 952f, 945f, 964f, 893f, 972f, 905f)

/** Padding of each letter's black piece, and the selected command's tilt (degrees, clockwise). */
private const val PIECE_PAD = 6f
private const val SELECTED_TILT = 7f

/** The selected command's black backing ends this many pads under each letter (about 6 px, as in the game). */
private const val SLAB_BOTTOM = 0.8f

/** Palettes for the letter colours a command takes: mint when selected, red and cyan flashes. */
private class Tints(palette: Palette) {
    val white = palette
    val mint = palette.copy(paper = MINT)
    val red = palette.copy(paper = CURSOR_RED)
    val cyan = palette.copy(paper = palette.highlight)
}

/**
 * The command words over Joker's hand. Unselected words are prerendered bitmaps; they slide in on
 * the opening. A move pops the new word (it grows for ~90 ms, red at first, then snaps to size)
 * while the old one shrinks back flashing cyan and red; the picker glides, flashes speed lines and
 * regrows, then wobbles for as long as the menu is open.
 */
@Composable
fun CommandList(entries: List<Entry>, state: MenuState, time: DoubleState, palette: Palette, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 160)
    val density = LocalDensity.current
    val fonts = LocalFontFamilyResolver.current
    // 37.sp gives the game's 54 px capitals; the selected command's are 61 px.
    val words = remember(entries, density, fonts) { entries.map { RansomWord.cut(it.label, measurer, density, 37.sp, outlined = true, fonts = fonts) } }
    val selectedLabel = entries.getOrNull(state.selected)?.label.orEmpty()
    val previousLabel = entries.getOrNull(state.previous)?.label.orEmpty()
    val big = remember(selectedLabel, density, fonts) { RansomWord.cut(selectedLabel, measurer, density, 42.sp, outlined = true, fonts = fonts) }
    val previousBig = remember(previousLabel, density, fonts) { RansomWord.cut(previousLabel, measurer, density, 42.sp, outlined = true, fonts = fonts) }
    // Read at draw time so a selection change does not rebuild the cache (and re-prerender every word).
    val selectedWord = rememberUpdatedState(big)
    val previousWord = rememberUpdatedState(previousBig)
    val tints = remember(palette) { Tints(palette) }
    val picker = remember { Picker() }

    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val pad = PIECE_PAD * k
        val rows = words.map { word -> prerenderWord(word, palette, pad) }

        onDrawBehind {
            val t = time.doubleValue
            val n = gameFrame(t, state.openedAt)
            if (!Opening.wordsVisible(n)) return@onDrawBehind
            val visible = minOf(entries.size, MAX_VISIBLE)
            val first = (state.selected - visible / 2).coerceIn(0, entries.size - visible)
            val moved = state.selectedAt > state.openedAt
            val since = t - state.selectedAt
            val m = gameFrame(t, state.selectedAt)
            val popping = moved && since < CursorChange.POP_SECONDS
            val oldScale = if (popping) CursorChange.oldScale(m) else null
            val oldSlot = state.previous - first

            for (i in first until first + visible) {
                if (i == state.selected || (oldScale != null && i == state.previous)) continue
                val row = rows[i]
                val slot = i - first
                drawImage(
                    row.bitmap,
                    topLeft = Offset(
                        ROW_RIGHT[slot] * k - words[i].width - row.margin + Opening.wordOffset(slot, n) * k,
                        rowY(slot) * k - row.bitmap.height / 2f,
                    ),
                )
            }
            if (oldScale != null && oldSlot in 0 until visible) {
                val word = previousWord.value
                val letters = when (m) {
                    0 -> tints.cyan
                    1 -> tints.red
                    else -> tints.white
                }
                atRow(word, oldSlot, k, 0f, 0f) {
                    scale(oldScale, oldScale, word.centre) { drawRansomOutlined(word, letters, pad * 1.2f) }
                }
            }

            val slot = state.selected - first
            if (slot !in 0 until visible) return@onDrawBehind
            val word = selectedWord.value
            val glide = if (popping) CursorChange.glide(m) else null
            val regrow = if (moved) ((since - CursorChange.POP_SECONDS) / CursorChange.GROW_SECONDS).toFloat().coerceIn(0f, 1f) else 1f
            val scale = if (popping) CursorChange.newScale(m) else 1f
            atRow(word, slot, k, 10f * k + Opening.wordOffset(slot, n) * k, SELECTED_TILT) {
                scale(scale, scale, word.centre) {
                    picker.draw(
                        this, word, pad * 1.2f, palette, t,
                        wedgeDy = if (glide != null) (1f - glide) * (oldSlot - slot) * ROW_STEP * k else 0f,
                        wedgeSize = when {
                            glide != null -> CursorChange.glideSize(m)
                            popping -> 0f
                            else -> regrow
                        },
                        extras = if (popping) 0f else regrow,
                        speedLines = if (popping && CursorChange.speedLines(m)) (m - 3) * 12f * k + 1f else 0f,
                    )
                    drawRansomOutlined(word, if (popping && m <= 1) tints.red else tints.mint, pad * 1.2f)
                    if (!popping) picker.recolour(this, word, pad * 1.2f, t, regrow)
                }
            }
        }
    })
}

private fun rowY(slot: Int) = ROW_TOP + slot * ROW_STEP

private val RansomWord.centre get() = Offset(width / 2, (inkTop + inkBottom) / 2)

/** Places a command at its row: right edge on the slot's edge plus [right], tilted about its centre. */
private inline fun DrawScope.atRow(word: RansomWord, slot: Int, k: Float, right: Float, degrees: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(ROW_RIGHT[slot] * k + right - word.width, rowY(slot) * k)
        rotate(degrees, word.centre)
    }, block)
}

/**
 * The game's cursor around the selected command, in word space (origin at the word's left edge,
 * vertically centred). Behind the word's black slab: a cyan wedge along its top edge ending in a tip
 * that points up-right, a red sliver on the top-left edge and a red tooth at the right; a cyan
 * triangle under the first letter. At rest the corners swing smoothly (periods 0.28-0.5 s, as
 * measured) and the small shapes keep collapsing and regrowing. Paths are reused between frames.
 */
private class Picker {
    private val slab = Path()
    private val wedge = Path()
    private val sliver = Path()
    private val tooth = Path()
    private val chip = Path()
    private val speedLeft = Path()
    private val speedRight = Path()
    private val recolourLeft = Path()
    private val recolourRight = Path()

    fun draw(
        scope: DrawScope,
        word: RansomWord,
        pad: Float,
        palette: Palette,
        t: Double,
        wedgeDy: Float,
        wedgeSize: Float,
        extras: Float,
        speedLines: Float,
    ) = with(scope) {
        val w = word.width
        val top = word.inkTop
        val bottom = word.inkBottom
        val h = bottom - top
        val mid = (top + bottom) / 2
        val sway = sin(2 * PI * t / 0.32).toFloat()

        val r = swing(t, 0.5, 0.3) * extras
        if (r > 0.02f) {
            val th = h * 0.32f * r
            sliver.quad(-pad * 2.6f, top - pad * 1.4f - th, w * 0.38f, top - pad - th * 0.35f, w * 0.38f, top - pad, -pad * 2.6f, top - pad * 1.4f)
            drawPath(sliver, CURSOR_RED)
        }
        val q = swing(t, 0.45, 1.9) * extras
        if (q > 0.02f) {
            tooth.tri(w + pad * 1.9f, mid + h * 0.05f, w + pad * 1.9f + h * 0.5f * q, mid + h * 0.32f, w + pad * 1.9f, mid + h * 0.5f)
            drawPath(tooth, CURSOR_RED)
        }
        if (wedgeSize > 0f) {
            val tipX = w + h * (0.55f + 0.35f * sway)
            val tipY = top - h * (0.62f + 0.15f * sway)
            wedge.five(
                w * 0.22f, top - pad * 1.2f,
                w - h * 0.1f, top - pad * 1.4f - h * 0.16f,
                tipX, tipY,
                w + pad * 1.6f, top + h * 0.2f,
                w * 0.24f, top - pad * 0.8f,
            )
            withTransform({
                translate(0f, wedgeDy)
                scale(wedgeSize, wedgeSize, Offset(w, top))
            }) { drawPath(wedge, palette.highlight) }
        }
        // Straight top edge (the wedge and sliver hang off it); the bottom steps along each letter's
        // ink, as the game's backing does. A straight bottom at the lowest letter, tilted with the
        // word, would cut into the next row under a long command's right end.
        slab.reset()
        slab.moveTo(-pad * 2.2f, top - pad * 1.4f)
        slab.lineTo(w + pad * 1.6f, top - pad)
        val glyphs = word.glyphs
        for (i in glyphs.indices.reversed()) {
            val g = glyphs[i]
            val y = g.inkBottom + pad * SLAB_BOTTOM
            slab.lineTo(if (i == glyphs.lastIndex) w + pad * 1.9f else g.centerX + g.boxW / 2, y)
            slab.lineTo(if (i == 0) -pad * 1.6f else g.centerX - g.boxW / 2, y)
        }
        if (glyphs.isEmpty()) slab.lineTo(w + pad * 1.9f, bottom + pad * SLAB_BOTTOM)
        slab.close()
        drawPath(slab, palette.ink)
        val c = swing(t, 0.28, 0.8) * extras
        if (c > 0.02f) {
            val lx = -w * 0.06f + w * 0.18f * (1f - c)
            val under = (glyphs.firstOrNull()?.inkBottom ?: bottom) + pad * SLAB_BOTTOM
            chip.tri(lx, under + pad * 1.4f, w * 0.3f, under + pad * 0.4f, w * 0.28f, under + pad * 2.6f)
            scale(c, c, Offset(w * 0.3f, under + pad * 1.2f)) { drawPath(chip, palette.highlight) }
        }
        if (speedLines > 0f) {
            val s = speedLines
            speedLeft.quad(-w * 0.08f - 110f * pad / 7f - s, mid + 9f, -w * 0.08f - s, mid - 2f, -w * 0.08f - s, mid + 16f, -w * 0.08f - 110f * pad / 7f - s, mid + 27f)
            speedRight.quad(w * 1.04f + s, mid - 14f, w * 1.04f + 115f * pad / 7f + s, mid - 34f, w * 1.04f + 115f * pad / 7f + s, mid - 16f, w * 1.04f + s, mid + 4f)
            drawPath(speedLeft, palette.highlight)
            drawPath(speedRight, palette.highlight)
        }
    }

    /** The red polygons that turn the letters under them red: the left of the first letter and the tail of the last. */
    fun recolour(scope: DrawScope, word: RansomWord, pad: Float, t: Double, size: Float) = with(scope) {
        if (size <= 0f) return@with
        val w = word.width
        val top = word.inkTop
        val bottom = word.inkBottom
        val h = bottom - top
        val mid = (top + bottom) / 2
        val leftEdge = w * (0.10f + 0.03f * sin(2 * PI * t / 0.4).toFloat()) * size
        recolourLeft.quad(-pad * 3f, top - pad * 2f, leftEdge, top - pad * 2f, leftEdge - w * 0.04f, bottom + pad * 2f, -pad * 3f, bottom + pad * 2f)
        clipPath(recolourLeft) { drawRansomLetters(word, CURSOR_RED) }
        val tail = w * (0.93f - 0.02f * sin(2 * PI * t / 0.37 + 1.0).toFloat())
        recolourRight.quad(tail, mid + h * 0.05f, w + pad * 3f, mid - h * 0.15f, w + pad * 3f, bottom + pad * 2f, tail - w * 0.03f, bottom + pad * 2f)
        clipPath(recolourRight) { drawRansomLetters(word, CURSOR_RED) }
    }

    /** 0..1 and back on a period, resting at 0 half the time: the shapes collapse and regrow. */
    private fun swing(t: Double, period: Double, phase: Double) = max(0f, sin(2 * PI * t / period + phase).toFloat())
}

private fun Path.tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
    reset()
    moveTo(ax, ay)
    lineTo(bx, by)
    lineTo(cx, cy)
    close()
}

private fun Path.quad(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) {
    reset()
    moveTo(ax, ay)
    lineTo(bx, by)
    lineTo(cx, cy)
    lineTo(dx, dy)
    close()
}

private fun Path.five(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, ex: Float, ey: Float) {
    reset()
    moveTo(ax, ay)
    lineTo(bx, by)
    lineTo(cx, cy)
    lineTo(dx, dy)
    lineTo(ex, ey)
    close()
}

private class PrerenderedWord(val bitmap: ImageBitmap, val margin: Float)

private fun CacheDrawScope.prerenderWord(word: RansomWord, palette: Palette, pad: Float): PrerenderedWord {
    val margin = pad * 4
    val bitmap = prerender(ceil(word.width + margin * 2).toInt(), ceil(word.height + margin * 2).toInt(), ImageBitmapConfig.Argb8888) {
        translate(margin, size.height / 2f) { drawRansomOutlined(word, palette, pad) }
    }
    return PrerenderedWord(bitmap, margin)
}

/**
 * The big background number ("01"): the game's digits in ink, each on its own crooked paper card
 * with a black wedge cut into it; the plate above covers their tops, as in the game. Prerendered per
 * number; a selection change just swaps it. It fades in with the stickers and jumps in the shake.
 */
@Composable
fun CommandNumber(state: MenuState, palette: Palette, time: DoubleState, modifier: Modifier = Modifier) {
    val digits = (0..9).map { d -> ImageBitmap.imageResource(BIG_DIGITS[d]) }
    val text = "%02d".format(state.selected + 1)
    Spacer(modifier.fillMaxSize().drawWithCache {
        val k = size.height / REF_H
        val glyphs = text.map { digits[it - '0'] }
        val bw = ceil(glyphs.sumOf { it.width } + DIGIT_STEP * (glyphs.size - 1) + CARD_MARGIN * 2).toInt()
        val bh = ceil(glyphs.maxOf { it.height } + DIGIT_DROP + CARD_MARGIN * 2).toInt()
        val bitmap = prerender(bw, bh, ImageBitmapConfig.Argb8888) {
            var x = CARD_MARGIN
            glyphs.forEachIndexed { n, g ->
                val at = Offset(x, CARD_MARGIN + if (n % 2 == 1) DIGIT_DROP else 0f)
                val (card, wedge) = card(n, at, g.width.toFloat(), g.height.toFloat())
                drawPath(card, ART)
                drawPath(card, palette.ink, style = Stroke(4f))
                drawPath(wedge, palette.ink)
                drawImage(g, topLeft = at, colorFilter = ColorFilter.tint(palette.ink))
                x += g.width + DIGIT_STEP
            }
        }
        onDrawBehind {
            val n = gameFrame(time.doubleValue, state.openedAt)
            scale(k, k, Offset.Zero) {
                shaken(Opening.numberShake(n), 1f, 0f, 0f) {
                    drawImage(bitmap, topLeft = Offset(NUMBER_X - CARD_MARGIN, NUMBER_Y - CARD_MARGIN), alpha = Opening.stickerAlpha(n))
                }
            }
        }
    })
}

/** First digit cell's top-left in the frame; cells overlap ([DIGIT_STEP] < 0) and the second sits lower. */
private const val NUMBER_X = 1540f
private const val NUMBER_Y = 421f
private const val DIGIT_STEP = -32f
private const val DIGIT_DROP = 12f
private const val CARD_MARGIN = 64f

/** Crooked paper card around a digit's cell and the black wedge cut into it; alternates per position. */
private fun card(n: Int, at: Offset, w: Float, h: Float): Pair<Path, Path> {
    val (x, y) = at
    return if (n % 2 == 0) {
        polygon(Offset(x - 58f, y + 44f), Offset(x + w + 6f, y - 8f), Offset(x + w + 12f, y + h + 14f), Offset(x - 14f, y + h + 28f)) to
            polygon(Offset(x - 58f, y + 44f), Offset(x - 2f, y + h * 0.64f), Offset(x - 40f, y + h * 0.6f))
    } else {
        polygon(Offset(x - 6f, y - 14f), Offset(x + w + 38f, y + 22f), Offset(x + w + 58f, y + h + 4f), Offset(x - 2f, y + h + 22f)) to
            polygon(Offset(x + w * 0.42f, y + h + 20f), Offset(x + w + 58f, y + h + 4f), Offset(x + w + 26f, y + h * 0.48f))
    }
}

private val BIG_DIGITS = intArrayOf(
    R.drawable.p5_big_digit_0, R.drawable.p5_big_digit_1, R.drawable.p5_big_digit_2, R.drawable.p5_big_digit_3,
    R.drawable.p5_big_digit_4, R.drawable.p5_big_digit_5, R.drawable.p5_big_digit_6, R.drawable.p5_big_digit_7,
    R.drawable.p5_big_digit_8, R.drawable.p5_big_digit_9,
)

private val MONEY_DIGITS = intArrayOf(
    R.drawable.p5_money_digit_0, R.drawable.p5_money_digit_1, R.drawable.p5_money_digit_2, R.drawable.p5_money_digit_3,
    R.drawable.p5_money_digit_4, R.drawable.p5_money_digit_5, R.drawable.p5_money_digit_6, R.drawable.p5_money_digit_7,
    R.drawable.p5_money_digit_8, R.drawable.p5_money_digit_9,
)

private val MONEY_RIMS = intArrayOf(
    R.drawable.p5_money_rim_0, R.drawable.p5_money_rim_1, R.drawable.p5_money_rim_2, R.drawable.p5_money_rim_3,
    R.drawable.p5_money_rim_4, R.drawable.p5_money_rim_5, R.drawable.p5_money_rim_6, R.drawable.p5_money_rim_7,
    R.drawable.p5_money_rim_8, R.drawable.p5_money_rim_9,
)

/**
 * Text ("21:05") in the camp menu's money digits, the game's "¥6,340" corner: white digits over
 * their ink outline layer. All outlines go down first, so they merge without covering a digit.
 * A colon is two round dots in the same style.
 */
@Composable
fun MoneyDigits(text: String, palette: Palette, modifier: Modifier = Modifier) {
    val digits = MONEY_DIGITS.map { ImageBitmap.imageResource(it) }
    val rims = MONEY_RIMS.map { ImageBitmap.imageResource(it) }
    val widthPx = text.sumOf { ch -> if (ch.isDigit()) rims[ch - '0'].width - DIGIT_OVERLAP else COLON_W } + DIGIT_OVERLAP
    val heightPx = rims[0].height
    val size = with(LocalDensity.current) { DpSize(widthPx.toDp(), heightPx.toDp()) }
    Spacer(modifier.size(size).drawWithCache {
        val ink = ColorFilter.tint(palette.ink)
        val paper = ColorFilter.tint(palette.paper)
        onDrawBehind {
            for (outline in booleanArrayOf(true, false)) {
                var x = 0f
                for (ch in text) {
                    if (ch.isDigit()) {
                        val d = ch - '0'
                        drawImage(if (outline) rims[d] else digits[d], topLeft = Offset(x, 0f), colorFilter = if (outline) ink else paper)
                        x += rims[d].width - DIGIT_OVERLAP
                        continue
                    }
                    // Colon: two round dots, white in an ink ring like the digits' outline layer.
                    for ((dx, fy) in COLON_DOTS) {
                        val c = Offset(x + COLON_W / 2f + dx, heightPx * fy)
                        if (outline) drawCircle(palette.ink, 12f, c) else drawCircle(palette.paper, 7f, c)
                    }
                    x += COLON_W
                }
            }
        }
    })
}

private const val DIGIT_OVERLAP = 10
private const val COLON_W = 26

/** Colon dots: x shift in px (the upper one leans right with the italic digits), y as a fraction. */
private val COLON_DOTS = arrayOf(3f to 0.36f, -3f to 0.68f)

/**
 * Line under "COMMAND" ("Use a Skill" in the game): the selected entry, with the bond's rank pips.
 * It fades and slides in on the opening and, as in the game, simply swaps on a move.
 */
@Composable
fun CommandCaption(text: String, rank: Int, state: MenuState, time: DoubleState, palette: Palette, modifier: Modifier = Modifier) {
    Row(
        modifier.graphicsLayer {
            val n = gameFrame(time.doubleValue, state.openedAt)
            alpha = Opening.captionAlpha(n)
            translationX = Opening.captionSlide(n) / 2f * density // frame px to dp at 1080p
            rotationZ = -3f
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(palette.ink, SkewShape(0.3f)).padding(horizontal = 14.dp, vertical = 2.dp)) {
            BasicText(text, style = TextStyle(fontFamily = Fonts.oswald, fontSize = 13.sp, color = palette.paper))
        }
        if (rank > 0) {
            Spacer(Modifier.width(10.dp))
            RankPips(rank, palette, Modifier.size(width = 90.dp, height = 10.dp))
        }
    }
}
