package com.chroniac.tvhome

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chroniac.tvhome.ui.LauncherScreen
import com.chroniac.tvhome.ui.MenuState
import com.chroniac.tvhome.ui.ThemeId
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {
    private lateinit var apps: AppRepository
    private lateinit var bonds: BondRepository
    private lateinit var weather: WeatherRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var sounds: MenuSounds
    private val menuState = MenuState()
    private var theme by mutableStateOf(ThemeId.Joker)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // An update sends REMOVED(replacing) then ADDED(replacing); reload once, on ADDED.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            lifecycleScope.launch { apps.reload() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = AppRepository(this)
        bonds = BondRepository(this)
        weather = WeatherRepository(this)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        theme = ThemeId.fromKey(prefs.getString(KEY_THEME, null)) ?: ThemeId.Joker
        sounds = MenuSounds(this)

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        lifecycleScope.launch { apps.reload() }
        // Ranks are recomputed on every return to the home screen (and whenever the app list changes).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                apps.apps.collect { list -> if (list.isNotEmpty()) bonds.refresh(list.map { it.key }) }
            }
        }

        setContent {
            val appList by apps.apps.collectAsState()
            val bondMap by bonds.bonds.collectAsState()
            val rankUps by bonds.rankUps.collectAsState()
            val now by weather.weather.collectAsState()
            val current = theme
            val shortcuts = remember(current) { shortcuts(current) }
            LauncherScreen(
                theme = current,
                apps = appList,
                shortcuts = shortcuts,
                bonds = bondMap,
                rankUps = rankUps,
                weather = now,
                state = menuState,
                onLaunch = ::launch,
                onMove = apps::move,
                onAppInfo = { app ->
                    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.key, null)))
                },
                onRankUpSeen = bonds::acknowledge,
                onCursor = sounds::cursor,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // The window is still hidden behind the system's transition here; the opening (and its sound)
        // starts once it is shown, in onEnterAnimationComplete.
        menuState.hold()
        lifecycleScope.launch { weather.refreshIfStale() }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        menuState.requestReplay()
        sounds.open()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while already on the home screen: jump back to the first entry.
        if (hasWindowFocus()) menuState.select(0)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        sounds.release()
        super.onDestroy()
    }

    private fun shortcuts(theme: ThemeId) = listOf(
        ShortcutEntry(
            key = "inputs",
            label = getString(R.string.inputs),
            iconRes = R.drawable.ic_input,
            description = getString(R.string.inputs_description),
            action = ShortcutAction.Launch(Intent("com.android.tv.action.VIEW_INPUTS")),
        ),
        ShortcutEntry(
            key = "settings",
            label = getString(R.string.settings),
            iconRes = R.drawable.ic_settings,
            description = getString(R.string.settings_description),
            action = ShortcutAction.Launch(Intent(Settings.ACTION_SETTINGS)),
        ),
        ShortcutEntry(
            key = "theme",
            label = getString(R.string.theme_entry),
            iconRes = R.drawable.ic_theme,
            description = getString(R.string.theme_description, getString(theme.title), getString(theme.next().title)),
            action = ShortcutAction.CycleTheme,
        ),
    )

    private fun launch(entry: Entry) {
        when (entry) {
            is AppEntry -> startSafely(entry.launchIntent)
            is ShortcutEntry -> when (val action = entry.action) {
                is ShortcutAction.Launch -> startSafely(action.intent)
                ShortcutAction.CycleTheme -> cycleTheme()
            }
        }
    }

    private fun cycleTheme() {
        theme = theme.next()
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        menuState.requestReplay()
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val PREFS = "home"
        const val KEY_THEME = "theme"
    }
}
