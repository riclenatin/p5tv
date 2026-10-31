package com.chroniac.tvhome.ui

import kotlin.math.floor

/*
 * Timing of P5R's camp menu, measured frame by frame on 60 fps footage of the game: the opening
 * (frame 0 = the first menu frame) and the cursor change (frame 0 = the first frame after the
 * key press). The game steps these effects once per frame and does not ease between frames, so
 * they are tables indexed by frame rather than curves. Offsets are px in a 1920x1080 frame.
 */

internal const val GAME_FPS = 60.0

/**
 * Whole game frames elapsed at [time] since [since]; -1 before it. The clamp matters: a held opening
 * sits at Double.MAX_VALUE, and the raw value would be Int.MIN_VALUE, which wraps on subtraction.
 */
internal fun gameFrame(time: Double, since: Double): Int = if (time < since) -1 else floor((time - since) * GAME_FPS).toInt()

internal object Opening {
    /** Joker's final art cuts in (before it: silhouettes). */
    const val ART = 18

    /** The command list appears. */
    const val LIST = 19

    /** First frame of the 7-frame impact shake; nothing moves from [ART] + 9 on. */
    private const val SHAKE = 20

    /** Stickers, plates and the big number fade in over two frames with the star panel. */
    fun stickerAlpha(n: Int) = when {
        n < 0 -> 0f
        n == 0 -> 0.5f
        else -> 1f
    }

    /** "COMMAND" fades in place just before the art. */
    fun commandAlpha(n: Int) = when {
        n < 15 -> 0f
        n == 15 -> 0.02f
        n == 16 -> 0.76f
        else -> 1f
    }

    private val HUD_FADE = floatArrayOf(0.42f, 0.63f, 0.79f, 0.90f, 0.98f)

    /** The money (our clock and chips): 6-frame fade from the art frame. */
    fun hudAlpha(n: Int) = if (n < ART) 0f else HUD_FADE.getOrElse(n - ART) { 1f }

    private val CAPTION_FADE = floatArrayOf(0.18f, 0.43f, 0.62f, 0.71f, 0.96f)
    private val CAPTION_SLIDE = floatArrayOf(26f, 21f, 15f, 7f, 1.6f)

    /** The hint under COMMAND fades in over 6 frames while sliding in from the right. */
    fun captionAlpha(n: Int) = if (n < ART) 0f else CAPTION_FADE.getOrElse(n - ART) { 1f }

    fun captionSlide(n: Int) = if (n < ART) 0f else CAPTION_SLIDE.getOrElse(n - ART) { 0f }

    /** Button prompts: 3-frame fade. */
    fun buttonsAlpha(n: Int) = when {
        n < 23 -> 0f
        n == 23 -> 0.33f
        n == 24 -> 0.67f
        else -> 1f
    }

    /** Each row's sideways start offset; they alternate in direction. */
    private val WORD_OFFSET = floatArrayOf(56f, -1f, 48f, -46f, -3f, 32f, 41f, -32f, -44f)

    /** What is left of the offset per frame: a quadratic ease-out over 4 frames, no overshoot. */
    private val WORD_LEFT = floatArrayOf(1f, 0.56f, 0.25f, 0.06f)

    fun wordsVisible(n: Int) = n >= LIST

    fun wordOffset(slot: Int, n: Int) = WORD_OFFSET[slot % WORD_OFFSET.size] * WORD_LEFT.getOrElse(n - LIST) { 0f }

    // Impact shake, one entry per frame. The layers jump independently: (dx, dy) or (dx, dy, scale).
    private val ART_SHAKE = arrayOf(
        floatArrayOf(35f, -12f, 1.025f), floatArrayOf(-15f, -7f, 1.025f), floatArrayOf(31f, 17f, 1.021f),
        floatArrayOf(-10f, -15f, 1.026f), floatArrayOf(8f, 27f, 1.015f), floatArrayOf(18f, -19f, 1.017f),
        floatArrayOf(13f, 25f, 1.014f),
    )
    private val BACKGROUND_SHAKE = arrayOf(
        floatArrayOf(5f, -15f, 1.025f), floatArrayOf(-48f, -10f, 1.027f), floatArrayOf(3f, 15f, 1.023f),
        floatArrayOf(-42f, -18f, 1.027f), floatArrayOf(-20f, 25f, 1.023f), floatArrayOf(-7f, -21f, 1.021f),
        floatArrayOf(-12f, 23f, 1.022f),
    )
    private val PLATE_SHAKE = arrayOf(
        floatArrayOf(-23f, -15f), floatArrayOf(28f, -1f), floatArrayOf(-20f, 2f), floatArrayOf(23f, -18f),
        floatArrayOf(3f, 25f), floatArrayOf(-8f, -21f), floatArrayOf(-3f, 23f),
    )
    private val STICKER_SHAKE = arrayOf(
        floatArrayOf(-23f, 15f), floatArrayOf(28f, 10f), floatArrayOf(-20f, -15f), floatArrayOf(23f, 18f),
        floatArrayOf(3f, -25f), floatArrayOf(-8f, 21f), floatArrayOf(-3f, -23f),
    )
    private val NUMBER_SHAKE = arrayOf(
        floatArrayOf(23f, -15f), floatArrayOf(-28f, -1f), floatArrayOf(20f, 2f), floatArrayOf(-23f, -18f),
        floatArrayOf(-3f, 26f), floatArrayOf(8f, -21f), floatArrayOf(3f, 25f),
    )

    /** Joker's art: offset and zoom about the finger anchor (865, 107). */
    fun artShake(n: Int): FloatArray? = ART_SHAKE.getOrNull(n - SHAKE)

    /** The room and star panel: offset and zoom about (225, 540). */
    fun backgroundShake(n: Int): FloatArray? = BACKGROUND_SHAKE.getOrNull(n - SHAKE)

    /** The "MENU: Main" plate. */
    fun plateShake(n: Int): FloatArray? = PLATE_SHAKE.getOrNull(n - SHAKE)

    /** "Dont Look at", "LIKE THAT!" and the team-name plate. */
    fun stickerShake(n: Int): FloatArray? = STICKER_SHAKE.getOrNull(n - SHAKE)

    fun numberShake(n: Int): FloatArray? = NUMBER_SHAKE.getOrNull(n - SHAKE)
}

internal object CursorChange {
    /** The new word grows for about this long, then snaps to its resting size. */
    const val POP_SECONDS = 0.09

    /** Then the picker regrows from almost nothing into its idle loop. */
    const val GROW_SECONDS = 0.1

    /** New word: 0.86x its selected size on the first frame, +0.14x per frame (about its centre). */
    fun newScale(m: Int) = 0.86f + 0.14f * m

    private val OLD_SCALE = floatArrayOf(0.96f, 0.90f, 0.86f, 0.80f)

    /** Old word, relative to its selected size, while it shrinks; null once it is back in the list. */
    fun oldScale(m: Int): Float? = OLD_SCALE.getOrNull(m)

    /** The cyan wedge glides to the new row in steps of 41, 16 and 15 px (of 72) and shrinks. */
    private val GLIDE = floatArrayOf(0.57f, 0.79f, 1f)
    private val GLIDE_SIZE = floatArrayOf(0.9f, 0.75f, 0.6f)

    fun glide(m: Int): Float? = GLIDE.getOrNull(m)

    fun glideSize(m: Int) = GLIDE_SIZE[m]

    /** Frames 3 and 4: cyan speed slivers off both ends of the new word. */
    fun speedLines(m: Int) = m == 3 || m == 4
}
