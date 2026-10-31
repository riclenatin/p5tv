package com.chroniac.tvhome.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.pow
import kotlin.math.sin

/*
 * All motion is computed from the Compose frame clock instead of Animatable/animate*AsState.
 * This TV runs with animator_duration_scale=0 (set system-wide for speed), and Compose's
 * animation APIs honour that scale, so they would snap to their end values. withFrameNanos
 * is not scaled, and Compose pauses it while the activity is stopped.
 *
 * Time is kept in Double seconds: the launcher process lives for weeks, and a Float clock would
 * lose frame-level resolution after a day and a half. Only small deltas become Float.
 */

fun progress(now: Double, start: Double, duration: Float, delay: Float = 0f): Float =
    ((now - start - delay) / duration).coerceIn(0.0, 1.0).toFloat()

/** Ambient oscillation in [-1, 1]; safe for arbitrarily large [time]. */
fun wave(time: Double, speed: Double, phase: Double = 0.0): Float = sin(time * speed + phase).toFloat()

fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

fun easeOutBack(x: Float, overshoot: Float = 1.70158f): Float {
    val t = x - 1f
    return 1f + (overshoot + 1f) * t * t * t + overshoot * t * t
}

fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t

private const val SCROLL_SECONDS = 0.34f

/** [MenuState.openedAt] while the opening waits for the window to appear (nothing but the backdrop shows). */
private const val HELD = Double.MAX_VALUE

/** If the window-shown callback never arrives, the opening starts by itself after this long. */
private const val MAX_HOLD_SECONDS = 0.75

/** Menu selection, the timestamps the draw code animates from, and idle tracking. */
@Stable
class MenuState {
    var selected by mutableIntStateOf(0)
        private set

    /** Selection before the last change; the hero eases its camera from it. */
    var previous by mutableIntStateOf(0)
        private set

    /** Clock time of the last selection change or entrance replay; 0 plays the intro on the first frame. */
    var selectedAt by mutableDoubleStateOf(0.0)
        private set

    /**
     * Clock time the menu was last opened (shown again); the opening animation runs from it. Held at
     * an unreachable time between [hold] and the next replay, so only the backdrop shows.
     */
    var openedAt by mutableDoubleStateOf(HELD)
        private set

    private var startNanos = -1L
    private var scrollFrom = 0f
    private var replayPending = false
    private var heldSince = -1.0
    private var lastInteraction = 0.0
    private val wake = MutableStateFlow(0)

    /** Advances the clock from a frame timestamp and returns the frame time in seconds. */
    fun onFrame(frameNanos: Long): Double {
        if (startNanos < 0) startNanos = frameNanos
        val time = (frameNanos - startNanos) / 1e9
        if (openedAt == HELD) {
            if (heldSince < 0) heldSince = time else if (time - heldSince > MAX_HOLD_SECONDS) replayPending = true
        }
        if (replayPending) {
            replayPending = false
            heldSince = -1.0
            scrollFrom = selected.toFloat()
            previous = selected
            selectedAt = time
            openedAt = time
            lastInteraction = time
        }
        return time
    }

    /** Wall-clock time on the frame clock's base; valid even while frames are paused. */
    fun now(): Double = if (startNanos < 0) 0.0 else (System.nanoTime() - startNanos) / 1e9

    /** Fractional list position at [time]; eases from the previous selection with a small overshoot. */
    fun scrollAt(time: Double): Float =
        mix(scrollFrom, selected.toFloat(), easeOutBack(progress(time, selectedAt, SCROLL_SECONDS), 1.1f))

    fun select(index: Int) {
        if (index == selected) return
        val t = now()
        scrollFrom = scrollAt(t)
        previous = selected
        selected = index
        selectedAt = t
        poke()
    }

    /** Replays the entrance on the next frame (the clock is stale while the activity is stopped). */
    fun requestReplay() {
        replayPending = true
        wake.value++
    }

    /**
     * Keeps the opening from starting until [requestReplay]: the window is still hidden behind the
     * system's transition when the activity starts, and the opening would play unseen.
     */
    fun hold() {
        openedAt = HELD
        heldSince = -1.0
        wake.value++
    }

    /** Marks user activity: full frame rate resumes. */
    fun poke() {
        lastInteraction = now()
        wake.value++
    }

    fun idleSeconds(): Double = now() - lastInteraction

    /** Suspends until the next poke/replay; used to stop drawing entirely on a long-idle screen. */
    suspend fun awaitWake() {
        val seen = wake.value
        wake.first { it != seen }
    }

    fun clampTo(size: Int) {
        if (size > 0 && selected >= size) select(size - 1)
    }
}
