package com.chroniac.tvhome.ui

import androidx.annotation.StringRes
import com.chroniac.tvhome.R

enum class ThemeLayout {
    /** Persona 5 command menu: list over a white hand, Joker centre-right, star panel. */
    Phantom,

    /** Selected app's banner as a tilted card on the left. */
    Portrait,
}

enum class ThemeId(@param:StringRes val title: Int, val palette: Palette, val layout: ThemeLayout) {
    Joker(R.string.theme_joker, PhantomPalette, ThemeLayout.Phantom),
    Collage(R.string.theme_collage, PhantomPalette, ThemeLayout.Portrait);

    fun next(): ThemeId = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Null when nothing is saved or the saved theme no longer exists. */
        fun fromKey(key: String?): ThemeId? = entries.firstOrNull { it.name == key }
    }
}
