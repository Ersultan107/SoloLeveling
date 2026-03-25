package com.sololeveling.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.sololeveling.app.R
import com.sololeveling.app.SoloLevelingApp
import com.sololeveling.app.data.model.*
import com.sololeveling.app.data.repository.UserRepository
import com.sololeveling.app.ui.main.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: UserRepository

    // Emergency exit tracking
    private var tapCount = 0
    private var tapResetJob: Job? = null
    private var holdStartTime = 0L

    companion object {
        fun show(context: Context, quest: Quest? = null, message: String? = null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                quest?.let { putExtra("quest_id", it.id) }
                message?.let { putExtra("message", it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as SoloLevelingApp).repository
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(SoloLevelingApp.NOTIF_ID_FOREGROUND + 1, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message")
        val questId = intent?.getLongExtra("quest_id", -1L)

        serviceScope.launch {
            val safeMode = repository.isSafeMode().firstOrNull() ?: false
            val isPaused = repository.isPaused().firstOrNull() ?: false

            if (!safeMode && !isPaused) {
                showOverlay(message, questId)
            } else {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
    }

    // ─── Overlay Display ──────────────────────────────────────────────────────

    private suspend fun showOverlay(message: String?, questId: Long?) {
        if (overlayView != null) removeOverlay()

        val profile = repository.getUserProfileOnce()

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_notification, null)

        // Configure overlay view
        setupOverlayContent(overlayView!!, profile, message, questId)
        setupEmergencyExit(overlayView!!)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        try {
            windowManager.addView(overlayView, params)
            animateIn(overlayView!!)

            // Auto-dismiss after 8 seconds
            delay(8000)
            animateOut(overlayView!!)
            delay(300)
            removeOverlay()
            stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun setupOverlayContent(
        view: View,
        profile: UserProfile?,
        message: String?,
        questId: Long?
    ) {
        view.findViewById<TextView>(R.id.tvOverlayTitle)?.text =
            profile?.aiName ?: "ARIA"
        view.findViewById<TextView>(R.id.tvOverlayMessage)?.text =
            message ?: "Новое задание ждёт тебя!"
        view.findViewById<TextView>(R.id.tvOverlayRank)?.text =
            profile?.rank?.displayName ?: "E-Rank"

        // Open app on tap
        view.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                questId?.let { putExtra("quest_id", it) }
            }
            startActivity(intent)
            removeOverlay()
            stopSelf()
        }

        // Dismiss button
        view.findViewById<ImageButton>(R.id.btnDismissOverlay)?.setOnClickListener {
            removeOverlay()
            stopSelf()
        }

        // Hint text
        view.findViewById<TextView>(R.id.tvEscapeHint)?.text = "Удержи 5с или тапни 3 раза для экстренного выхода"
    }

    // ─── Emergency Exit ───────────────────────────────────────────────────────

    private fun setupEmergencyExit(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStartTime = System.currentTimeMillis()

                    // Count taps for triple-tap exit
                    tapCount++
                    tapResetJob?.cancel()
                    tapResetJob = serviceScope.launch {
                        delay(1000)
                        tapCount = 0
                    }

                    if (tapCount >= 3) {
                        emergencyExit()
                        return@setOnTouchListener true
                    }

                    // Start hold detection
                    serviceScope.launch {
                        delay(5000)
                        if (holdStartTime > 0) {
                            emergencyExit()
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    holdStartTime = 0L
                    false
                }
                else -> false
            }
        }
    }

    private fun emergencyExit() {
        Toast.makeText(this, "Экстренный выход активирован", Toast.LENGTH_SHORT).show()
        serviceScope.launch {
            repository.setPaused(true)
            AIBackgroundService.stop(this@OverlayService)
        }
        removeOverlay()
        stopSelf()
    }

    // ─── Animation ────────────────────────────────────────────────────────────

    private fun animateIn(view: View) {
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        anim.duration = 300
        view.startAnimation(anim)
    }

    private fun animateOut(view: View) {
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
        anim.duration = 300
        view.startAnimation(anim)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View may already be removed
            }
            overlayView = null
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, SoloLevelingApp.CHANNEL_FOREGROUND)
            .setContentTitle("Solo Leveling Overlay")
            .setContentText("Активен")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
