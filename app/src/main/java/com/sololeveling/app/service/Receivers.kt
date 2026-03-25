package com.sololeveling.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sololeveling.app.SoloLevelingApp
import kotlinx.coroutines.*

// ─── Accessibility Service ────────────────────────────────────────────────────

class SoloLevelingAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Packages that should NEVER be blocked or interrupted
    private val protectedPackages = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "com.android.vending", // Google Play
        "com.sololeveling.app"  // This app itself
    )

    // Focus mode detection - popular focus/productivity apps
    private val focusModeApps = setOf(
        "com.flightyapp.flighty",
        "com.forestapp.forest",
        "com.focuskeeper.app",
        "com.mindfultech.calm",
        "com.calm.android",
        "com.headspace.android"
    )

    private var currentForegroundPackage = ""
    private var isFocusModeActive = false

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentForegroundPackage = packageName

            // Detect focus mode apps
            isFocusModeActive = packageName in focusModeApps

            // Notify the system about context changes
            updateSystemContext(packageName)
        }
    }

    private fun updateSystemContext(packageName: String) {
        serviceScope.launch {
            val app = application as SoloLevelingApp
            val repo = app.repository

            // If user is in protected or focus app, pause overlay and notifications
            val shouldPauseOverlay = packageName in protectedPackages ||
                    isFocusModeActive ||
                    isStudyOrWorkApp(packageName)

            if (shouldPauseOverlay) {
                // Signal to suppress overlay/notifications temporarily
                // The OverlayService checks this before showing
            }
        }
    }

    private fun isStudyOrWorkApp(packageName: String): Boolean {
        val studyKeywords = listOf("study", "work", "focus", "learn", "edu", "office", "docs", "sheets")
        return studyKeywords.any { packageName.lowercase().contains(it) }
    }

    override fun onInterrupt() {
        serviceScope.cancel()
    }

    fun getCurrentPackage(): String = currentForegroundPackage
    fun isFocusModeDetected(): Boolean = isFocusModeActive
    fun isProtectedApp(pkg: String): Boolean = pkg in protectedPackages
}

// ─── Boot Receiver ────────────────────────────────────────────────────────────

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            // Restart background service after boot
            AIBackgroundService.start(context)
        }
    }
}

// ─── Alarm Receiver ───────────────────────────────────────────────────────────

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MORNING_ALARM = "com.sololeveling.MORNING_ALARM"
        const val ACTION_EVENING_ALARM = "com.sololeveling.EVENING_ALARM"
        const val ACTION_SLEEP_REMINDER = "com.sololeveling.SLEEP_REMINDER"
        const val ACTION_QUEST_EXPIRY = "com.sololeveling.QUEST_EXPIRY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MORNING_ALARM -> handleMorningAlarm(context)
            ACTION_EVENING_ALARM -> handleEveningAlarm(context)
            ACTION_SLEEP_REMINDER -> handleSleepReminder(context)
            ACTION_QUEST_EXPIRY -> handleQuestExpiry(context)
        }
    }

    private fun handleMorningAlarm(context: Context) {
        AIBackgroundService.start(context)
    }

    private fun handleEveningAlarm(context: Context) {
        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

        val notification = androidx.core.app.NotificationCompat
            .Builder(context, SoloLevelingApp.CHANNEL_AI_MESSAGES)
            .setContentTitle("⚔️ Вечерний отчёт")
            .setContentText("Подведём итоги дня? Открой приложение для анализа прогресса.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notifMgr.notify(SoloLevelingApp.NOTIF_ID_AI_MESSAGE + 10, notification)
    }

    private fun handleSleepReminder(context: Context) {
        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

        val notification = androidx.core.app.NotificationCompat
            .Builder(context, SoloLevelingApp.CHANNEL_SYSTEM)
            .setContentTitle("🌙 Время сна")
            .setContentText("Хороший сон — основа продуктивности. Пора ложиться!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notifMgr.notify(SoloLevelingApp.NOTIF_ID_AI_MESSAGE + 20, notification)
    }

    private fun handleQuestExpiry(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val repo = UserRepository(context)
            repo.expireOldQuests()
        }
    }
}
