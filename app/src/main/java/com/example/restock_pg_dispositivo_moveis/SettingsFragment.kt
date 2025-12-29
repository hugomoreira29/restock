package com.example.restock_pg_dispositivo_moveis

// HUGO MOREIRA - a22402246

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.data.SettingsManager
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        settingsManager = SettingsManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeSettings()
    }

    private fun setupClickListeners() {
        binding.accountButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_account)
        }

        binding.familyButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_familyFragment)
        }

        binding.languageButton.setOnClickListener {
            showLanguageSelectionDialog()
        }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                settingsManager.setNotificationsEnabled(isChecked)
            }
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.notificationsEnabledFlow.collect { areEnabled ->
                binding.notificationsSwitch.isChecked = areEnabled
            }
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("Português", "English", "Sistema (Padrão)")
        val languageCodes = arrayOf("pt", "en", "system")

        viewLifecycleOwner.lifecycleScope.launch {
            val currentLanguageCode = settingsManager.languageFlow.first()
            val checkedItem = languageCodes.indexOf(currentLanguageCode).takeIf { it >= 0 } ?: 2

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_language))
                .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                    val selectedLocale = languageCodes[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        settingsManager.setLanguage(selectedLocale)
                        
                        // Reinicia a aplicação para aplicar a nova língua
                        val intent = Intent(requireActivity(), SplashActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        requireActivity().finish()
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
