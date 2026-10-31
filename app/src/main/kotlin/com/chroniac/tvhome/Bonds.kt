package com.chroniac.tvhome

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Persona-style "confidant" bond with an app: a fixed arcana and a rank 0..10 earned by
 * foreground time over the last 30 days.
 */
class Bond(val arcana: Int, val rank: Int, val minutes: Long)

data class RankUp(val packageName: String, val from: Int, val to: Int, val arcana: Int)

/**
 * Reads usage stats (needs the GET_USAGE_STATS app-op, granted by install.sh). Without it the
 * system returns no stats and every bond stays at rank 0.
 */
class BondRepository(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _bonds = MutableStateFlow<Map<String, Bond>>(emptyMap())
    val bonds: StateFlow<Map<String, Bond>> = _bonds.asStateFlow()
    private val _rankUps = MutableStateFlow<List<RankUp>>(emptyList())
    /** Rank increases the user has not been shown yet. */
    val rankUps: StateFlow<List<RankUp>> = _rankUps.asStateFlow()

    suspend fun refresh(packages: Collection<String>) {
        val minutes = withContext(Dispatchers.IO) { foregroundMinutes() }
        val bonds = packages.associateWith { pkg ->
            val m = minutes[pkg] ?: 0L
            Bond(arcanaOf(pkg), rankFor(m), m)
        }
        val ups = ArrayList<RankUp>()
        val edit = prefs.edit()
        for ((pkg, bond) in bonds) {
            val key = SEEN_PREFIX + pkg
            if (!prefs.contains(key)) {
                edit.putInt(key, bond.rank) // first sight: no rank-up fanfare for existing usage
            } else {
                val seen = prefs.getInt(key, 0)
                if (bond.rank > seen) ups += RankUp(pkg, seen, bond.rank, bond.arcana)
            }
        }
        edit.apply()
        _bonds.value = bonds
        _rankUps.value = ups
    }

    fun acknowledge(rankUp: RankUp) {
        prefs.edit().putInt(SEEN_PREFIX + rankUp.packageName, rankUp.to).apply()
        _rankUps.value = _rankUps.value - rankUp
    }

    private fun foregroundMinutes(): Map<String, Long> {
        val end = System.currentTimeMillis()
        val begin = end - WINDOW_DAYS * 24L * 60 * 60 * 1000
        return runCatching { usage.queryAndAggregateUsageStats(begin, end) }
            .getOrDefault(emptyMap())
            // Foreground-service time counts too: music players (Spotify) mostly run that way.
            .mapValues { (it.value.totalTimeInForeground + it.value.totalTimeForegroundServiceUsed) / 60_000L }
    }

    companion object {
        const val WINDOW_DAYS = 30
        private const val PREFS = "bonds"
        private const val SEEN_PREFIX = "seen_"

        /** Hours of use (last 30 days) needed for ranks 1..10. */
        private val RANK_HOURS = floatArrayOf(0.5f, 2f, 5f, 9f, 14f, 20f, 27f, 35f, 44f, 54f)

        fun rankFor(minutes: Long): Int = RANK_HOURS.count { minutes >= it * 60 }

        /** Stable per package: String.hashCode is specified, so the arcana never changes. */
        fun arcanaOf(packageName: String): Int = (packageName.hashCode() and Int.MAX_VALUE) % 22
    }
}
