package com.sololeveling.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.*
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.ActivityOnboardingBinding
import com.sololeveling.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var userName = ""
    private var aiName = "ARIA"
    private var personality = AIPersonality.BALANCED

    private val pages = listOf(
        OnboardingPage(
            title = "⚔️ Solo Leveling",
            subtitle = "Система саморазвития",
            description = "Превратись в охотника своей жизни.\nКаждый день — новые квесты.\nКаждое достижение — новый уровень.",
            step = 0
        ),
        OnboardingPage(
            title = "🧠 Твой ИИ-Партнёр",
            subtitle = "Больше чем приложение",
            description = "Твой ИИ-ассистент станет настоящим другом и наставником. Он анализирует твой прогресс, заботится о твоём состоянии и помогает расти.",
            step = 1
        ),
        OnboardingPage(
            title = "🎯 Система Квестов",
            subtitle = "Геймификация жизни",
            description = "Ежедневные квесты, случайные задания и эпические босс-квесты. Система адаптируется под твоё состояние — проще в тяжёлые дни, сложнее на пике формы.",
            step = 2
        ),
        OnboardingPage(
            title = "🛡️ Безопасность",
            subtitle = "Умная, не навязчивая",
            description = "Система НЕ мешает учёбе, работе или сну. Тихий режим, безопасный режим, пауза — всё под твоим контролем.",
            step = 3
        ),
        OnboardingPage(
            title = "👤 Создай профиль",
            subtitle = "Последний шаг",
            description = "",
            step = 4
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter(pages, this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        binding.dotsIndicator.attachTo(binding.viewPager)
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                binding.viewPager.currentItem = current + 1
                updateButtonState(current + 1)
            }
        }

        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.currentItem = current - 1
                updateButtonState(current - 1)
            }
        }

        binding.btnFinish.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updateButtonState(page: Int) {
        binding.btnBack.visibility = if (page > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (page < pages.size - 1) View.VISIBLE else View.GONE
        binding.btnFinish.visibility = if (page == pages.size - 1) View.VISIBLE else View.GONE
    }

    fun setUserName(name: String) { userName = name }
    fun setAIName(name: String) { aiName = name }
    fun setPersonality(p: AIPersonality) { personality = p }

    private fun finishOnboarding() {
        if (userName.isBlank()) {
            Toast.makeText(this, "Введи своё имя!", Toast.LENGTH_SHORT).show()
            return
        }

        val profile = UserProfile(
            name = userName,
            aiName = aiName.ifBlank { "ARIA" },
            aiPersonality = personality
        )

        CoroutineScope(Dispatchers.IO).launch {
            val repo = (application as com.sololeveling.app.SoloLevelingApp).repository
            repo.saveUserProfile(profile)
            repo.setOnboardingDone(true)

            // Create welcome quest
            val welcomeQuest = Quest(
                title = "🌟 Первый шаг",
                description = "Открой чат с ИИ-ассистентом и познакомься с ним",
                type = QuestType.DAILY,
                difficulty = QuestDifficulty.EASY,
                xpReward = 50,
                coinReward = 25,
                category = QuestCategory.PRODUCTIVITY
            )
            repo.insertQuest(welcomeQuest)
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ─── Onboarding Data ──────────────────────────────────────────────────────────

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val step: Int
)

// ─── Onboarding Pager Adapter ─────────────────────────────────────────────────

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>,
    private val activity: OnboardingActivity
) : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], activity)
    }

    override fun getItemCount() = pages.size

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(page: OnboardingPage, activity: OnboardingActivity) {
            itemView.findViewById<TextView>(R.id.tvOnboardingTitle).text = page.title
            itemView.findViewById<TextView>(R.id.tvOnboardingSubtitle).text = page.subtitle

            val descView = itemView.findViewById<TextView>(R.id.tvOnboardingDescription)
            val setupLayout = itemView.findViewById<View>(R.id.layoutProfileSetup)

            if (page.step == 4) {
                // Profile setup step
                descView.visibility = View.GONE
                setupLayout.visibility = View.VISIBLE
                setupProfileStep(itemView, activity)
            } else {
                descView.text = page.description
                descView.visibility = View.VISIBLE
                setupLayout.visibility = View.GONE
            }
        }

        private fun setupProfileStep(view: View, activity: OnboardingActivity) {
            val etName = view.findViewById<EditText>(R.id.etOnboardingName)
            val etAIName = view.findViewById<EditText>(R.id.etOnboardingAIName)
            val rgPersonality = view.findViewById<RadioGroup>(R.id.rgOnboardingPersonality)

            etName.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    activity.setUserName(s?.toString() ?: "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            etAIName.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    activity.setAIName(s?.toString() ?: "ARIA")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            rgPersonality.setOnCheckedChangeListener { _, id ->
                val p = when (id) {
                    R.id.rbOnboardingFriendly -> AIPersonality.FRIENDLY
                    R.id.rbOnboardingStrict -> AIPersonality.STRICT
                    else -> AIPersonality.BALANCED
                }
                activity.setPersonality(p)
            }
        }
    }
}
