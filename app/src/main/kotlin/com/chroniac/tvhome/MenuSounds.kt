package com.chroniac.tvhome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * The game's own menu sounds (res/raw, extracted by tools/p5r/make_assets.py): the cursor tick on
 * every selection change and the camp-menu open sound. SoundPool keeps them low-latency; they play
 * on the media stream so the TV's volume applies, at half level because the files peak at 0 dBFS.
 */
class MenuSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val cursor = pool.load(context, R.raw.p5_cursor, 1)
    private val open = pool.load(context, R.raw.p5_open, 1)
    private val loaded = HashSet<Int>()

    /** A sound requested before it finished loading (the open sound on a cold start) plays on load. */
    private var pending = 0

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loaded += id
            if (id == pending) {
                pending = 0
                play(id)
            }
        }
    }

    fun cursor() = play(cursor)

    fun open() = play(open)

    private fun play(id: Int) {
        if (id in loaded) pool.play(id, VOLUME, VOLUME, 1, 0, 1f) else pending = id
    }

    fun release() = pool.release()

    private companion object {
        const val VOLUME = 0.5f
    }
}
