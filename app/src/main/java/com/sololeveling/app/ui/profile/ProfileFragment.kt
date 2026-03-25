package com.sololeveling.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.sololeveling.app.R
import com.sololeveling.app.data.model.*
import com.sololeveling.app.databinding.FragmentProfileBinding
import com.sololeveling.app.service.AIBackgroundService
import com.sololeveling.app.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
        setupButtons()
    }

    private fun observeData() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let { renderProfile(it) }
        }
        viewModel.systemState.observe(viewLifecycleOwner) { state ->
            binding.switchSafeMode.isChecked = state.isSafeMode
            binding.switchPause.isChecked = state.isPaused
        }
    }

    private fun renderProfile(profile: UserProfile) {
        binding.tvProfileName.text = profile.name
        binding.tvProfileAIName.text = "ИИ: ${profile.aiName}"
        binding.tvProfileRank.text = profile.rank.displayName
        binding.tvProfileLevel.text = "Уровень ${profile.level}"
        binding.tvProfileXP.text = "${profile.xp} / ${profile.xpToNextLevel} XP"
        binding.tvProfileStreak.text = "${profile.currentStreak} дней подряд"
        binding.tvProfileCoins.text = "${profile.coins} монет"

        val personality = when (profile.aiPersonality) {
            AIPersonality.FRIENDLY -> "😊 Дружелюбный"
            AIPersonality.STRICT -> "💪 Строгий"
            AIPersonality.BALANCED -> "⚡ Сбалансированный"
        }
        binding.tvProfilePersonality.text = personality

        // Rank progress display
        val nextRank = getNextRank(profile.rank)
        if (nextRank != null) {
            binding.tvNextRank.text = "До ${nextRank.displayName}: ${nextRank.minLevel - profile.level} ур."
        } else {
            binding.tvNextRank.text = "Максимальный ранг достигнут! 🏆"
        }
    }

    private fun setupButtons() {
        binding.switchSafeMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleSafeMode()
        }

        binding.switchPause.setOnCheckedChangeListener { _, isChecked ->
            viewModel.togglePause()
        }

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnQuietHours.setOnClickListener {
            showQuietHoursDialog()
        }

        binding.btnSetApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnOverlayPermission.setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            ))
        }

        binding.btnRestartService.setOnClickListener {
            AIBackgroundService.stop(requireContext())
            AIBackgroundService.start(requireContext())
            Toast.makeText(requireContext(), "Сервис перезапущен", Toast.LENGTH_SHORT).show()
        }

        binding.btnEmergencyStop.setOnClickListener {
            showEmergencyStopDialog()
        }
    }

    private fun showEditProfileDialog() {
        val profile = viewModel.userProfile.value ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditName)
        val etAIName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditAIName)
        val rgPersonality = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgPersonality)

        etName.setText(profile.name)
        etAIName.setText(profile.aiName)

        when (profile.aiPersonality) {
            AIPersonality.FRIENDLY -> rgPersonality.check(R.id.rbFriendly)
            AIPersonality.STRICT -> rgPersonality.check(R.id.rbStrict)
            AIPersonality.BALANCED -> rgPersonality.check(R.id.rbBalanced)
        }

        AlertDialog.Builder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = etName.text?.toString()?.trim() ?: profile.name
                val newAIName = etAIName.text?.toString()?.trim() ?: profile.aiName
                val newPersonality = when (rgPersonality.checkedRadioButtonId) {
                    R.id.rbFriendly -> AIPersonality.FRIENDLY
                    R.id.rbStrict -> AIPersonality.STRICT
                    else -> AIPersonality.BALANCED
                }

                CoroutineScope(Dispatchers.IO).launch {
                    viewModel.repository.saveUserProfile(
                        profile.copy(
                            name = newName.ifEmpty { profile.name },
                            aiName = newAIName.ifEmpty { profile.aiName },
                            aiPersonality = newPersonality
                        )
                    )
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showQuietHoursDialog() {
        Toast.makeText(requireContext(), "Тихие часы: 22:00 – 08:00 (по умолчанию)", Toast.LENGTH_LONG).show()
    }

    private fun showApiKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)
        val etKey = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKey)

        AlertDialog.Builder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("API ключ Anthropic")
            .setMessage("Введите ваш API ключ Claude для активации ИИ-ассистента")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = etKey.text?.toString()?.trim() ?: return@setPositiveButton
                if (key.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        viewModel.repository.saveApiKey(key)
                    }
                    Toast.makeText(requireContext(), "API ключ сохранён", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEmergencyStopDialog() {
        AlertDialog.Builder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("⚠️ Экстренная остановка")
            .setMessage("Это остановит все службы Solo Leveling. Продолжить?")
            .setPositiveButton("Остановить") { _, _ ->
                AIBackgroundService.stop(requireContext())
                CoroutineScope(Dispatchers.IO).launch {
                    viewModel.repository.setPaused(true)
                    viewModel.repository.setSafeMode(true)
                }
                Toast.makeText(requireContext(), "Система остановлена", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun getNextRank(current: Rank): Rank? {
        val values = Rank.values()
        val idx = values.indexOf(current)
        return if (idx < values.size - 1) values[idx + 1] else null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
