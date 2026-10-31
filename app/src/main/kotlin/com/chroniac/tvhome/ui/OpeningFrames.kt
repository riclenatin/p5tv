package com.chroniac.tvhome.ui

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.FileNotFoundException

/**
 * The opening's silhouettes: Joker's menu model rendered from the game by tools/p5r/opening, as a
 * white and a black coverage mask per frame (assets/p5_opening, stored at 1/[scale] of the frame
 * size). Only the masks' alpha is kept, a few MB in all. [NONE] when the render step was skipped:
 * the opening then shows the backdrop alone until the art cuts in.
 */
internal class OpeningFrames(val scale: Int, val frames: List<Frame>) {
    class Frame(val white: Mask?, val black: Mask?)

    /** A coverage mask (ALPHA_8) and its top-left in the 1920x1080 frame. */
    class Mask(val image: ImageBitmap, val x: Int, val y: Int)

    companion object {
        private const val DIR = "p5_opening"

        val NONE = OpeningFrames(1, emptyList())

        /** Decodes every mask; call off the main thread. */
        fun load(assets: AssetManager): OpeningFrames {
            val index = try {
                JSONObject(assets.open("$DIR/index.json").bufferedReader().use { it.readText() })
            } catch (_: FileNotFoundException) {
                return NONE
            }
            fun mask(frame: JSONObject, key: String): Mask? {
                val entry = frame.optJSONObject(key) ?: return null
                val decoded = assets.open("$DIR/${entry.getString("file")}").use { BitmapFactory.decodeStream(it) }
                val alpha = decoded.extractAlpha()
                decoded.recycle()
                return Mask(alpha.asImageBitmap(), entry.getInt("x"), entry.getInt("y"))
            }
            val frames = index.getJSONArray("frames")
            return OpeningFrames(
                index.getInt("scale"),
                List(frames.length()) { i -> frames.getJSONObject(i).let { Frame(mask(it, "white"), mask(it, "black")) } },
            )
        }
    }
}
