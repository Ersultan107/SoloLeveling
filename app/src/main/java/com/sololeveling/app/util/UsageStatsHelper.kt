package com.sololeveling.app.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.sololeveling.app.data.model.AppUsageInfo
import java.util.*

object UsageStatsHelper {

    private const val TAG = "UsageStatsHelper"

    /**
     * Check if Usage Stats permission is granted.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Get app usage for the past N hours.
     */
    fun getUsageStats(context: Context, hours: Int = 24): List<AppUsageInfo> {
        if (!hasUsageStatsPermission(context)) return emptyList()

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (hours * 60 * 60 * 1000L)

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            val pm = context.packageManager

            stats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(20)
                .mapNotNull { stat ->
                    try {
                        val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        AppUsageInfo(
                            packageName = stat.packageName,
                            appName = appName,
                            usageTimeMs = stat.totalTimeInForeground,
                            category = categorizeApp(stat.packageName)
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage stats", e)
            emptyList()
        }
    }

    /**
     * Get the currently foreground app.
     */
    fun getCurrentForegroundApp(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val endTime = System.currentTimeMillis()
            val startTime = endTime - 5000L // Last 5 seconds

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate total screen time today in minutes.
     */
    fun getTodayScreenTimeMinutes(context: Context): Int {
        if (!hasUsageStatsPermission(context)) return 0

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            val startTime = cal.timeInMillis

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                System.currentTimeMillis()
            )

            val totalMs = stats
                .filter { !isSystemApp(it.packageName) }
                .sumOf { it.totalTimeInForeground }

            (totalMs / 60_000).toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if user is likely in a focus/work session right now.
     */
    fun isLikelyInFocusSession(context: Context): Boolean {
        val currentApp = getCurrentForegroundApp(context) ?: return false
        return isFocusApp(currentApp) || isProductivityApp(currentApp)
    }

    /**
     * Check if user is likely sleeping (no phone usage for 4+ hours at night).
     */
    fun isLikelySleeping(context: Context): Boolean {
        if (!hasUsageStatsPermission(context)) return false

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour !in 22..23 && currentHour !in 0..8) return false

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (4 * 60 * 60 * 1000L) // Last 4 hours

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            val recentUsage = stats.sumOf { it.totalTimeInForeground }
            recentUsage < (5 * 60 * 1000L) // Less than 5 minutes in 4 hours
        } catch (e: Exception) {
            false
        }
    }

    // ─── App Classification ───────────────────────────────────────────────────

    private fun categorizeApp(packageName: String): String {
        return when {
            isSocialApp(packageName) -> "Social"
            isProductivityApp(packageName) -> "Productivity"
            isFocusApp(packageName) -> "Focus"
            isEntertainmentApp(packageName) -> "Entertainment"
            isSystemApp(packageName) -> "System"
            else -> "Other"
        }
    }

    private fun isSocialApp(pkg: String): Boolean {
        val socialApps = listOf(
            "com.instagram.android", "com.twitter.android", "com.facebook.katana",
            "com.snapchat.android", "com.tiktok", "com.reddit.frontpage",
            "org.telegram.messenger", "com.whatsapp", "com.vk.android",
            "com.discord", "com.linkedin.android"
        )
        return socialApps.any { pkg.contains(it.substringAfterLast('.').take(8)) }
    }

    private fun isProductivityApp(pkg: String): Boolean {
        val productivityKeywords = listOf(
            "notion", "todoist", "trello", "slack", "teams", "docs", "sheets",
            "drive", "office", "word", "excel", "outlook", "gmail", "calendar",
            "notes", "keep", "evernote", "obsidian", "bear"
        )
        return productivityKeywords.any { pkg.lowercase().contains(it) }
    }

    private fun isFocusApp(pkg: String): Boolean {
        val focusApps = listOf(
            "com.forestapp.forest", "com.flightyapp", "com.focuskeeper",
            "io.finch.focus", "com.opal.app", "com.uselong.android"
        )
        return focusApps.any { pkg.contains(it.substringAfterLast('.').take(6)) }
    }

    private fun isEntertainmentApp(pkg: String): Boolean {
        val entertainmentKeywords = listOf(
            "youtube", "netflix", "spotify", "tiktok", "twitch", "gaming",
            "game", "play", "video", "movie", "music", "podcast"
        )
        return entertainmentKeywords.any { pkg.lowercase().contains(it) }
    }

    private fun isSystemApp(pkg: String): Boolean {
        return pkg.startsWith("com.android.") || pkg.startsWith("android.") ||
                pkg.startsWith("com.google.android.") || pkg == "com.sololeveling.app"
    }

    /**
     * Get usage summary for AI analysis context.
     */
    fun getUsageSummary(context: Context): Map<String, Int> {
        val stats = getUsageStats(context, 24)
        val summary = mutableMapOf<String, Int>()

        summary["social_minutes"] = stats
            .filter { it.category == "Social" }
            .sumOf { (it.usageTimeMs / 60_000).toInt() }

        summary["productivity_minutes"] = stats
            .filter { it.category == "Productivity" }
            .sumOf { (it.usageTimeMs / 60_000).toInt() }

        summary["entertainment_minutes"] = stats
            .filter { it.category == "Entertainment" }
            .sumOf { (it.usageTimeMs / 60_000).toInt() }

        summary["total_screen_time"] = getTodayScreenTimeMinutes(context)

        return summary
    }
}
