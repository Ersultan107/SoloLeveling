package com.sololeveling.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.ActivityMainBinding
import com.sololeveling.app.service.AIBackgroundService
import com.sololeveling.app.ui.chat.ChatActivity
import com.sololeveling.app.ui.onboarding.OnboardingActivity
import com.sololeveling.app.ui.quest.QuestFragment
import com.sololeveling.app.ui.profile.ProfileFragment
import com.sololeveling.app.ui.analytics.AnalyticsFragment
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val isOnboarded = viewModel.repository.isOnboardingDone().firstOrNull() ?: false
            if (!isOnboarded) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }

            setupUI()
            requestPermissions()
            startServices()
            observeData()
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun setupUI() {
        // Bottom navigation setup
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment())
                    true
                }
                R.id.nav_quests -> {
                    showFragment(QuestFragment())
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    false
                }
                R.id.nav_analytics -> {
                    showFragment(AnalyticsFragment())
                    true
                }
                R.id.nav_profile -> {
                    showFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Show home fragment by default
        if (supportFragmentManager.fragments.isEmpty()) {
            showFragment(HomeFragment())
        }

        // FAB - quick chat with AI
        binding.fabChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    private fun observeData() {
        viewModel.levelUpEvent.observe(this) { event ->
            event?.let { (level, rank) ->
                showLevelUpDialog(level, rank)
                viewModel.clearLevelUpEvent()
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }

        viewModel.userProfile.observe(this) { profile ->
            profile?.let { updateHeader(it) }
        }

        viewModel.systemState.observe(this) { state ->
            updateSystemStateUI(state)
        }
    }

    private fun updateHeader(profile: UserProfile) {
        binding.tvPlayerName.text = profile.name
        binding.tvRank.text = profile.rank.displayName
        binding.tvLevel.text = "Ур. ${profile.level}"

        val xpProgress = ((profile.xp.toFloat() / profile.xpToNextLevel) * 100).toInt()
        binding.progressXP.progress = xpProgress
        binding.tvXP.text = "${profile.xp}/${profile.xpToNextLevel} XP"
    }

    private fun updateSystemStateUI(state: SystemState) {
        val pauseColor = if (state.isPaused) R.color.neon_orange else R.color.neon_blue
        binding.btnPause.setColorFilter(ContextCompat.getColor(this, pauseColor))

        val safeModeColor = if (state.isSafeMode) R.color.neon_green else R.color.text_secondary
        binding.btnSafeMode.setColorFilter(ContextCompat.getColor(this, safeModeColor))
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("open_chat", false)) {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    private fun showLevelUpDialog(level: Int, rank: Rank) {
        val dialog = LevelUpDialog.newInstance(level, rank)
        dialog.show(supportFragmentManager, "level_up")
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun requestPermissions() {
        // Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun startServices() {
        AIBackgroundService.start(this)
    }
}
