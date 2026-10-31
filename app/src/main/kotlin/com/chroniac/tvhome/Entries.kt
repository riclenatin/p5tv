package com.chroniac.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Collections

/** One row of the home menu. */
sealed interface Entry {
    val key: String
    val label: String
}

/** A launchable TV app. [banner] is the 16:9 leanback art; [icon] is only loaded when it is missing. */
class AppEntry(
    override val key: String,
    override val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val launchIntent: Intent,
) : Entry

sealed interface ShortcutAction {
    class Launch(val intent: Intent) : ShortcutAction

    /** Handled in-app: switches to the next theme. */
    data object CycleTheme : ShortcutAction
}

/** Fixed shortcut, always listed after the apps. */
class ShortcutEntry(
    override val key: String,
    override val label: String,
    @param:DrawableRes val iconRes: Int,
    /** One line for the info strip under the hero. */
    val description: String,
    val action: ShortcutAction,
) : Entry

/** Installed leanback apps in the user's order. Order survives uninstall/reinstall cycles. */
class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) { queryApps() }
        _apps.value = ordered(loaded)
    }

    /** Moves [entry] by [delta] positions and persists the resulting order. */
    fun move(entry: AppEntry, delta: Int) {
        val keys = _apps.value.map { it.key }.toMutableList()
        val from = keys.indexOf(entry.key)
        val to = from + delta
        if (from < 0 || to !in keys.indices) return
        Collections.swap(keys, from, to)
        val uninstalled = storedOrder().filter { it !in keys }
        prefs.edit().putString(KEY_ORDER, (keys + uninstalled).joinToString(",")).apply()
        _apps.value = ordered(_apps.value)
    }

    private fun queryApps(): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(query, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { activity ->
                val banner = activity.loadBanner(pm)
                AppEntry(
                    key = activity.packageName,
                    label = activity.loadLabel(pm).toString(),
                    banner = banner,
                    icon = if (banner == null) activity.loadIcon(pm) else null,
                    launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(ComponentName(activity.packageName, activity.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    /** Stored order first; apps never ordered by the user follow alphabetically. */
    private fun ordered(apps: List<AppEntry>): List<AppEntry> {
        val order = storedOrder()
        val byKey = apps.associateBy { it.key }
        return order.mapNotNull { byKey[it] } + apps.filter { it.key !in order }
    }

    private fun storedOrder(): List<String> =
        prefs.getString(KEY_ORDER, null)?.split(',')?.filter { it.isNotEmpty() } ?: DEFAULT_ORDER

    private companion object {
        const val PREFS = "home"
        const val KEY_ORDER = "order"

        /** First-run order, copied from the stock launcher's favourites row on this TV. */
        val DEFAULT_ORDER = listOf(
            "com.netflix.ninja",
            "com.mediatek.wwtv.tvcenter",
            "com.google.android.youtube.tv",
            "ru.kinopoisk.tv",
            "tv.twitch.android.app",
            "com.spotify.tv.android",
            "com.android.vending",
        )
    }
}
