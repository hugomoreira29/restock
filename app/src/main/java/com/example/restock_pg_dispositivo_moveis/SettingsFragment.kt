package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.accountButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_account)
        }

        binding.familyButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_familyFragment)
        }

        binding.languageButton.setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("Português", "English", "Sistema (Padrão)")
        val languageCodes = arrayOf("pt", "en", "system")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_language))
            .setItems(languages) { _, which ->
                val selectedLocale = languageCodes[which]
                ThemeUtils.applyAndSaveLocale(requireContext(), selectedLocale)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
