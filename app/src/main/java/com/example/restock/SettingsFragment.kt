package com.example.restock

// HUGO MOREIRA - a22402246

// Android - para reiniciar a aplicação ao mudar o idioma ou tema
import android.content.Intent
import android.os.Bundle

// AndroidX - para o fragmento, coroutines e navegação
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

// Classes internas da aplicação - recursos, gestor de definições e binding
import com.example.restock.R
import com.example.restock.data.SettingsManager
import com.example.restock.databinding.FragmentSettingsBinding

// Material Design - para os diálogos de seleção de idioma e tema
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Coroutines - para ler e guardar as preferências de forma assíncrona
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pelo ecrã de definições da aplicação.
 * Permite ao utilizador gerir as notificações, o idioma, o tema
 * e navegar para as secções de conta, família e gamificação.
 * As preferências são guardadas de forma persistente com o DataStore.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Gestor de preferências persistentes da aplicação
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

    /**
     * Configura os listeners de clique para navegar entre secções
     * e gerir as preferências de notificações, idioma e tema.
     */
    private fun setupClickListeners() {
        // Navegação para o ecrã de conta
        binding.accountButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_account)
        }

        // Navegação para o ecrã de gestão da família
        binding.familyButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_familyFragment)
        }

        // Navegação para o ecrã de gamificação
        binding.gamificationButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_gamificationFragment)
        }

        // Abre o diálogo de seleção de idioma
        binding.languageButton.setOnClickListener {
            showLanguageSelectionDialog()
        }

        // Abre o diálogo de seleção de tema
        binding.themeButton.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Guarda o estado das notificações sempre que o switch é alterado
        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                settingsManager.setNotificationsEnabled(isChecked)
            }
        }
    }

    /**
     * Observa o estado das notificações no DataStore e atualiza o switch
     * sempre que o valor muda, sem disparar o listener de forma indevida.
     */
    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.notificationsEnabledFlow.collect { areEnabled ->
                binding.notificationsSwitch.isChecked = areEnabled
            }
        }
    }

    /**
     * Apresenta um diálogo de seleção de idioma com a opção atual pré-selecionada.
     * Ao selecionar um idioma, guarda a preferência e reinicia a aplicação para aplicar a mudança.
     */
    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("Português", "English", "Sistema (Padrão)")
        val languageCodes = arrayOf("pt", "en", "system")

        viewLifecycleOwner.lifecycleScope.launch {
            // Obtém o idioma atual para pré-selecionar a opção correta no diálogo
            val currentLanguageCode = settingsManager.languageFlow.first()
            val checkedItem = languageCodes.indexOf(currentLanguageCode).takeIf { it >= 0 } ?: 2

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_language))
                .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                    val selectedLocale = languageCodes[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        settingsManager.setLanguage(selectedLocale)

                        // Reinicia a aplicação a partir da SplashActivity para aplicar o novo idioma
                        val intent = Intent(requireActivity(), SplashActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        requireActivity().finish()
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Apresenta um diálogo de seleção de tema com a opção atual pré-selecionada.
     * Ao selecionar um tema, guarda a preferência e reinicia a aplicação para aplicar a mudança.
     */
    private fun showThemeSelectionDialog() {
        val themes = arrayOf("Claro", "Escuro", "Sistema (Padrão)")
        val themeCodes = arrayOf("light", "dark", "system")

        viewLifecycleOwner.lifecycleScope.launch {
            // Obtém o tema atual para pré-selecionar a opção correta no diálogo
            val currentThemeCode = settingsManager.themeFlow.first()
            val checkedItem = themeCodes.indexOf(currentThemeCode).takeIf { it >= 0 } ?: 2

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Tema")
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val selectedTheme = themeCodes[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        settingsManager.setTheme(selectedTheme)

                        // Reinicia a aplicação a partir da SplashActivity para aplicar o novo tema
                        val intent = Intent(requireActivity(), SplashActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        requireActivity().finish()
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}