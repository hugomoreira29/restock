package com.example.restock.ui.gamification

// Android - para gerir o ciclo de vida e vistas do fragmento
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

// AndroidX - para o ViewModel, coroutines, navegação e RecyclerView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.FragmentGamificationBinding

// Coroutines - para observar os fluxos de dados do ViewModel
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pelo ecrã de gamificação do utilizador.
 * Apresenta o nível, os pontos acumulados, a barra de progresso
 * para o próximo nível e a lista de emblemas desbloqueados.
 */
class GamificationFragment : Fragment() {

    private var _binding: FragmentGamificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GamificationViewModel by viewModels()
    private lateinit var badgeAdapter: BadgeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    /**
     * Configura a toolbar com o botão de navegação para voltar ao ecrã anterior.
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * Inicializa o adaptador de emblemas e configura o RecyclerView.
     */
    private fun setupRecyclerView() {
        badgeAdapter = BadgeAdapter()
        binding.badgesRecyclerView.apply {
            adapter = badgeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    /**
     * Observa os fluxos de dados do ViewModel e atualiza a interface
     * com o nível, pontos e progresso do utilizador, e a lista de emblemas.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.user.collect { user ->
                user?.let {
                    binding.levelTextView.text = getString(com.example.restock.R.string.your_level, it.level)
                    binding.pointsTextView.text = getString(com.example.restock.R.string.points_total, it.points)

                    // Calcula o progresso dentro do nível atual (cada nível tem 100 pontos)
                    binding.levelProgressBar.progress = it.points % 100
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.badges.collect { badges ->
                badgeAdapter.submitList(badges)
            }
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
