package com.sololeveling.app.ui.analytics

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.FragmentAnalyticsBinding
import com.sololeveling.app.ui.main.MainViewModel

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
        binding.btnRefreshAnalysis.setOnClickListener { viewModel.refreshAnalysis() }
    }

    private fun observeData() {
        viewModel.currentAnalysis.observe(viewLifecycleOwner) { analysis ->
            analysis?.let { renderAnalysis(it) }
        }

        viewModel.recentSleep.observe(viewLifecycleOwner) { records ->
            renderSleepChart(records)
        }

        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let { renderProfileStats(it) }
        }
    }

    private fun renderAnalysis(analysis: AIAnalysis) {
        binding.tvOverallScore.text = "${analysis.overallScore}"
        binding.tvMoodLabel.text = analysis.mood
        binding.tvRecommendationText.text = analysis.recommendation

        binding.progressSleepAnalytics.progress = analysis.sleepScore
        binding.progressProductivityAnalytics.progress = analysis.productivityScore
        binding.progressFitnessAnalytics.progress = analysis.fitnessScore

        binding.tvSleepScoreAnalytics.text = "${analysis.sleepScore}%"
        binding.tvProductivityScoreAnalytics.text = "${analysis.productivityScore}%"
        binding.tvFitnessScoreAnalytics.text = "${analysis.fitnessScore}%"

        val difficultyText = when {
            analysis.suggestedDifficultyMultiplier < 0.8f -> "Лёгкий (восстановление)"
            analysis.suggestedDifficultyMultiplier < 1.1f -> "Стандартный"
            analysis.suggestedDifficultyMultiplier < 1.3f -> "Повышенный"
            else -> "Элитный (ты в форме!)"
        }
        binding.tvDifficultyMode.text = difficultyText
    }

    private fun renderSleepChart(records: List<SleepRecord>) {
        if (records.isEmpty()) return

        val entries = records.reversed().mapIndexed { index, record ->
            Entry(index.toFloat(), record.quality.toFloat())
        }

        val dataSet = LineDataSet(entries, "Качество сна").apply {
            color = requireContext().getColor(R.color.neon_blue)
            valueTextColor = requireContext().getColor(R.color.text_primary)
            lineWidth = 2f
            circleRadius = 4f
            setCircleColor(requireContext().getColor(R.color.neon_blue))
            fillAlpha = 80
            setDrawFilled(true)
            fillColor = requireContext().getColor(R.color.neon_blue)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val days = records.reversed().map { record ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = record.date
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}.${cal.get(java.util.Calendar.MONTH) + 1}"
        }

        binding.chartSleep.apply {
            data = LineData(dataSet)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(days)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = requireContext().getColor(R.color.text_secondary)
                granularity = 1f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textColor = requireContext().getColor(R.color.text_secondary)
            }
            axisRight.isEnabled = false
            legend.textColor = requireContext().getColor(R.color.text_primary)
            description.isEnabled = false
            setBackgroundColor(requireContext().getColor(R.color.card_background))
            invalidate()
        }
    }

    private fun renderProfileStats(profile: UserProfile) {
        binding.tvStatStreak.text = "${profile.currentStreak}"
        binding.tvStatTotalQuests.text = "${profile.totalQuestsCompleted}"
        binding.tvStatLevel.text = "${profile.level}"
        binding.tvStatCoins.text = "${profile.coins}"
        binding.tvStatLongestStreak.text = "${profile.longestStreak}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
