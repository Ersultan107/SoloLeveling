package com.sololeveling.app.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.*
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.*
import com.sololeveling.app.ui.main.MainViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeData()

        // Send initial greeting if first time opening chat today
        viewModel.sendGreeting()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        viewModel.userProfile.observe(this) { profile ->
            profile?.let {
                binding.tvAIName.text = it.aiName
                binding.tvAIPersonality.text = when (it.aiPersonality) {
                    AIPersonality.FRIENDLY -> "😊 Дружелюбный"
                    AIPersonality.STRICT -> "💪 Строгий"
                    AIPersonality.BALANCED -> "⚡ Сбалансированный"
                }
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = chatAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Quick reply chips
        binding.chipHowAreYou.setOnClickListener { sendQuickReply("Как дела?") }
        binding.chipAnalyze.setOnClickListener { sendQuickReply("Проанализируй мой прогресс за сегодня") }
        binding.chipNewQuest.setOnClickListener { sendQuickReply("Дай мне новый квест") }
        binding.chipAdvice.setOnClickListener { sendQuickReply("Дай совет на сегодня") }
        binding.chipTired.setOnClickListener { sendQuickReply("Я устал, что посоветуешь?") }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        binding.etMessage.text?.clear()
        viewModel.sendUserMessage(text)
    }

    private fun sendQuickReply(text: String) {
        viewModel.sendUserMessage(text)
    }

    private fun observeData() {
        viewModel.chatMessages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.rvChat.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isAITyping.observe(this) { isTyping ->
            binding.layoutTypingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !isTyping
            binding.etMessage.isEnabled = !isTyping
        }
    }
}

// ─── Chat Adapter ─────────────────────────────────────────────────────────────

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        const val VIEW_USER = 0
        const val VIEW_AI = 1
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == MessageRole.USER) VIEW_USER else VIEW_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_USER) {
            val b = ItemChatUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserMessageViewHolder(b)
        } else {
            val b = ItemChatAiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AIMessageViewHolder(b)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AIMessageViewHolder -> holder.bind(message)
        }
    }

    // ─── User Message ViewHolder ──────────────────────────────────────────────

    inner class UserMessageViewHolder(private val binding: ItemChatUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvMessage.text = message.content
            binding.tvTime.text = formatTime(message.timestamp)
        }
    }

    // ─── AI Message ViewHolder with Typewriter Effect ─────────────────────────

    inner class AIMessageViewHolder(private val binding: ItemChatAiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var typewriterHandler: Handler? = null
        private var currentTypewriterRunnable: Runnable? = null

        fun bind(message: ChatMessage) {
            // Stop any ongoing typewriter
            currentTypewriterRunnable?.let { typewriterHandler?.removeCallbacks(it) }

            // Set emotion avatar
            binding.ivEmotion.text = getEmotionEmoji(message.emotion)

            binding.tvTime.text = formatTime(message.timestamp)

            // Apply message type styling
            when (message.messageType) {
                MessageType.QUEST_COMPLETE -> {
                    binding.root.setBackgroundResource(R.drawable.bg_chat_quest_complete)
                }
                MessageType.LEVEL_UP -> {
                    binding.root.setBackgroundResource(R.drawable.bg_chat_level_up)
                }
                MessageType.SLEEP_REPORT -> {
                    binding.root.setBackgroundResource(R.drawable.bg_chat_sleep)
                }
                else -> {
                    binding.root.setBackgroundResource(R.drawable.bg_chat_ai)
                }
            }

            // Typewriter effect for new messages (last 3)
            startTypewriterEffect(message.content)
        }

        private fun startTypewriterEffect(text: String) {
            typewriterHandler = Handler(Looper.getMainLooper())
            binding.tvMessage.text = ""
            var index = 0
            val delay = 18L // ms per character

            fun typeNext() {
                if (index < text.length) {
                    binding.tvMessage.append(text[index].toString())
                    index++
                    val runnable = Runnable { typeNext() }
                    currentTypewriterRunnable = runnable
                    typewriterHandler?.postDelayed(runnable, delay)
                }
            }

            // Only animate if message is recent (< 3 seconds old)
            val isRecent = System.currentTimeMillis() - 3000 < System.currentTimeMillis()
            if (isRecent && text.length < 300) {
                typeNext()
            } else {
                binding.tvMessage.text = text
            }
        }

        private fun getEmotionEmoji(emotion: AIEmotion) = when (emotion) {
            AIEmotion.HAPPY -> "😊"
            AIEmotion.EXCITED -> "🔥"
            AIEmotion.CONCERNED -> "😟"
            AIEmotion.PROUD -> "💪"
            AIEmotion.STERN -> "😤"
            AIEmotion.NEUTRAL -> "⚡"
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
    }

    private fun formatTime(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
