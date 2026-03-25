package com.sololeveling.app.ui.quest

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.*
import com.sololeveling.app.ui.main.MainViewModel

class QuestFragment : Fragment() {

    private var _binding: FragmentQuestBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var questAdapter: QuestAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQuestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
        setupTabs()
    }

    private fun setupRecyclerView() {
        questAdapter = QuestAdapter { quest ->
            viewModel.completeQuest(quest)
        }
        binding.rvQuests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = questAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }

    private fun observeData() {
        viewModel.activeQuests.observe(viewLifecycleOwner) { quests ->
            val sorted = quests.sortedWith(
                compareByDescending<Quest> { it.type == QuestType.BOSS }
                    .thenByDescending { it.isSpecial }
                    .thenByDescending { it.difficulty.multiplier }
            )
            questAdapter.submitList(sorted)
            binding.tvEmptyQuests.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupTabs() {
        binding.chipAll.setOnClickListener {
            viewModel.activeQuests.value?.let { questAdapter.submitList(it) }
        }
        binding.chipDaily.setOnClickListener {
            viewModel.activeQuests.value?.let { quests ->
                questAdapter.submitList(quests.filter { it.type == QuestType.DAILY })
            }
        }
        binding.chipBoss.setOnClickListener {
            viewModel.activeQuests.value?.let { quests ->
                questAdapter.submitList(quests.filter { it.type == QuestType.BOSS })
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class QuestAdapter(
    private val onComplete: (Quest) -> Unit
) : ListAdapter<Quest, QuestAdapter.QuestViewHolder>(QuestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder {
        val binding = ItemQuestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QuestViewHolder(private val binding: ItemQuestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(quest: Quest) {
            binding.tvQuestTitle.text = quest.title
            binding.tvQuestDescription.text = quest.description
            binding.tvQuestXP.text = "+${quest.xpReward} XP"
            binding.tvQuestCoins.text = "+${quest.coinReward} 💰"
            binding.tvQuestDifficulty.text = getDifficultyLabel(quest.difficulty)
            binding.tvQuestCategory.text = getCategoryEmoji(quest.category)
            binding.tvQuestType.text = getTypeLabel(quest.type)

            val diffColor = getDifficultyColor(quest.difficulty)
            binding.tvQuestDifficulty.setTextColor(diffColor)
            binding.viewDifficultyIndicator.setBackgroundColor(diffColor)

            if (quest.type == QuestType.BOSS || quest.isSpecial) {
                binding.root.setBackgroundResource(R.drawable.bg_boss_quest)
                binding.ivBossIcon.visibility = View.VISIBLE
            } else {
                binding.root.setBackgroundResource(R.drawable.bg_quest_card)
                binding.ivBossIcon.visibility = View.GONE
            }

            binding.btnCompleteQuest.setOnClickListener {
                onComplete(quest)
            }

            // Progress bar
            if (quest.targetValue > 1) {
                binding.progressQuest.visibility = View.VISIBLE
                binding.progressQuest.max = quest.targetValue
                binding.progressQuest.progress = quest.currentValue
                binding.tvQuestProgress.text = "${quest.currentValue}/${quest.targetValue}"
                binding.tvQuestProgress.visibility = View.VISIBLE
            } else {
                binding.progressQuest.visibility = View.GONE
                binding.tvQuestProgress.visibility = View.GONE
            }
        }

        private fun getDifficultyLabel(diff: QuestDifficulty) = when (diff) {
            QuestDifficulty.EASY -> "ЛЁГКИЙ"
            QuestDifficulty.NORMAL -> "ОБЫЧНЫЙ"
            QuestDifficulty.HARD -> "СЛОЖНЫЙ"
            QuestDifficulty.EPIC -> "ЭПИЧЕСКИЙ"
            QuestDifficulty.LEGENDARY -> "ЛЕГЕНДАРНЫЙ"
        }

        private fun getDifficultyColor(diff: QuestDifficulty): Int {
            val ctx = binding.root.context
            return when (diff) {
                QuestDifficulty.EASY -> ctx.getColor(R.color.difficulty_easy)
                QuestDifficulty.NORMAL -> ctx.getColor(R.color.difficulty_normal)
                QuestDifficulty.HARD -> ctx.getColor(R.color.difficulty_hard)
                QuestDifficulty.EPIC -> ctx.getColor(R.color.difficulty_epic)
                QuestDifficulty.LEGENDARY -> ctx.getColor(R.color.difficulty_legendary)
            }
        }

        private fun getCategoryEmoji(cat: QuestCategory) = when (cat) {
            QuestCategory.HEALTH -> "❤️ Здоровье"
            QuestCategory.PRODUCTIVITY -> "⚡ Продуктивность"
            QuestCategory.LEARNING -> "📚 Обучение"
            QuestCategory.FITNESS -> "💪 Фитнес"
            QuestCategory.MINDFULNESS -> "🧘 Осознанность"
            QuestCategory.SOCIAL -> "🤝 Социальное"
            QuestCategory.CREATIVITY -> "🎨 Творчество"
            QuestCategory.SLEEP -> "🌙 Сон"
        }

        private fun getTypeLabel(type: QuestType) = when (type) {
            QuestType.DAILY -> "ЕЖЕДНЕВНЫЙ"
            QuestType.RANDOM -> "СЛУЧАЙНЫЙ"
            QuestType.BOSS -> "БОСС-КВЕСТ"
            QuestType.SLEEP -> "СОН"
            QuestType.WEEKLY -> "ЕЖЕНЕДЕЛЬНЫЙ"
        }
    }

    class QuestDiffCallback : DiffUtil.ItemCallback<Quest>() {
        override fun areItemsTheSame(old: Quest, new: Quest) = old.id == new.id
        override fun areContentsTheSame(old: Quest, new: Quest) = old == new
    }
}
