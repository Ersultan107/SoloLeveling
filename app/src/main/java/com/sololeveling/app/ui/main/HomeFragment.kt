package com.sololeveling.app.ui.main

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeData()
    }

    private fun setupUI() {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        binding.tvDate.text = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }

        binding.btnSleepIn.setOnClickListener { viewModel.recordSleepStart() }
        binding.btnWakeUp.setOnClickListener { viewModel.recordWakeUp() }
        binding.btnPauseSystem.setOnClickListener { viewModel.togglePause() }
        binding.btnSafeMode.setOnClickListener { viewModel.toggleSafeMode() }
    }

    private fun observeData() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let { updateProfileCard(it) }
        }

        viewModel.currentAnalysis.observe(viewLifecycleOwner) { analysis ->
            analysis?.let { updateAnalysisCard(it) }
        }

        viewModel.activeQuests.observe(viewLifecycleOwner) { quests ->
            updateQuestSummary(quests)
        }

        viewModel.systemState.observe(viewLifecycleOwner) { state ->
            updateSystemStateIndicator(state)
        }
    }

    private fun updateProfileCard(profile: UserProfile) {
        binding.tvHeroName.text = profile.name
        binding.tvHeroRank.text = profile.rank.displayName
        binding.tvHeroLevel.text = "Уровень ${profile.level}"
        binding.tvStreak.text = "🔥 ${profile.currentStreak} дней"
        binding.tvCoins.text = "💰 ${profile.coins}"
        binding.tvTotalQuests.text = "⚔️ ${profile.totalQuestsCompleted} квестов"

        val xpProgress = ((profile.xp.toFloat() / profile.xpToNextLevel) * 100).toInt()
        binding.progressXP.progress = xpProgress
        binding.tvXPLabel.text = "${profile.xp} / ${profile.xpToNextLevel} XP"
    }

    private fun updateAnalysisCard(analysis: AIAnalysis) {
        binding.tvMoodStatus.text = analysis.mood
        binding.tvOverallScore.text = "${analysis.overallScore}%"
        binding.progressSleep.progress = analysis.sleepScore
        binding.progressProductivity.progress = analysis.productivityScore
        binding.progressFitness.progress = analysis.fitnessScore
        binding.tvSleepScore.text = "${analysis.sleepScore}%"
        binding.tvProductivityScore.text = "${analysis.productivityScore}%"
        binding.tvFitnessScore.text = "${analysis.fitnessScore}%"
        binding.tvRecommendation.text = analysis.recommendation
    }

    private fun updateQuestSummary(quests: List<Quest>) {
        val activeCount = quests.count { it.status == QuestStatus.ACTIVE }
        val bossQuest = quests.firstOrNull { it.type == QuestType.BOSS }

        binding.tvActiveQuestCount.text = "$activeCount активных квестов"
        binding.layoutBossQuest.visibility = if (bossQuest != null) View.VISIBLE else View.GONE
        bossQuest?.let {
            binding.tvBossQuestTitle.text = it.title
            binding.tvBossQuestDesc.text = it.description
        }
    }

    private fun updateSystemStateIndicator(state: SystemState) {
        val statusText = when {
            state.isSafeMode -> "⚠️ Безопасный режим"
            state.isPaused -> "⏸ Система на паузе"
            state.isFocusModeActive -> "🎯 Режим фокуса"
            state.batteryLevel < 15 -> "🔋 Тихий режим (батарея)"
            else -> "⚡ Система активна"
        }
        binding.tvSystemStatus.text = statusText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── Level Up Dialog ──────────────────────────────────────────────────────────

class LevelUpDialog : androidx.fragment.app.DialogFragment() {

    companion object {
        fun newInstance(level: Int, rank: Rank): LevelUpDialog {
            return LevelUpDialog().apply {
                arguments = Bundle().apply {
                    putInt("level", level)
                    putString("rank", rank.name)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_level_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val level = arguments?.getInt("level") ?: 1
        val rank = Rank.valueOf(arguments?.getString("rank") ?: "E")

        view.findViewById<android.widget.TextView>(R.id.tvLevelUpLevel)?.text = "УРОВЕНЬ $level"
        view.findViewById<android.widget.TextView>(R.id.tvLevelUpRank)?.text = rank.displayName
        view.findViewById<android.widget.Button>(R.id.btnLevelUpClose)?.setOnClickListener {
            dismiss()
        }

        // Auto dismiss after 5 seconds
        view.postDelayed({ dismissAllowingStateLoss() }, 5000)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
