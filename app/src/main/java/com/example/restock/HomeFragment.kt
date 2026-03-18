package com.example.restock

// Android - para gerir o ciclo de vida e vistas do fragmento
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// AndroidX - para visibilidade de vistas, ViewModels partilhados, coroutines e navegação
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

// Glide - para carregar a fotografia de perfil do utilizador
import com.bumptech.glide.Glide

// Classes internas da aplicação - binding, modelos e ViewModels
import com.example.restock.databinding.FragmentHomeBinding
import com.example.restock.model.Product
import com.example.restock.model.ShoppingListItem
import com.example.restock.ui.budget.BudgetViewModel
import com.example.restock.ui.inventario.InventarioViewModel
import com.example.restock.ui.lista.ShoppingListViewModel

// Firebase - autenticação para obter os dados do utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para combinar e observar múltiplos fluxos de dados
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Java - para gerar IDs únicos e calcular intervalos de tempo
import java.util.UUID
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246
 * Fragmento principal da aplicação após o login do utilizador.
 * Apresenta uma visão geral com o resumo do inventário, estado do orçamento,
 * produtos a expirar em breve e sugestões de compra geradas automaticamente
 * com base no histórico de consumo e na validade dos produtos.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModels partilhados com a Activity para aceder aos dados em tempo real
    private val inventoryViewModel: InventarioViewModel by activityViewModels()
    private val shoppingListViewModel: ShoppingListViewModel by activityViewModels()
    private val budgetViewModel: BudgetViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUserData()
        setupObservers()
        setupClickListeners()
    }

    /**
     * Carrega e apresenta os dados do utilizador autenticado.
     * Mostra o primeiro nome e a fotografia de perfil no cabeçalho.
     */
    private fun setupUserData() {
        val user = auth.currentUser
        if (user != null && user.displayName != null) {
            // Usa apenas o primeiro nome para a saudação
            val userName = user.displayName?.split(" ")?.firstOrNull() ?: getString(R.string.greeting_user)
            binding.greetingTextView.text = getString(R.string.greeting_hello, userName)

            Glide.with(this)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_avatar)
                .circleCrop()
                .into(binding.profileImageView)
        } else {
            binding.greetingTextView.text = getString(R.string.greeting_user)
        }
    }

    /**
     * Configura os observadores dos fluxos de dados dos ViewModels.
     * Combina os dados do inventário, histórico e lista de compras num único coletor
     * para atualizar o resumo e as sugestões de forma eficiente.
     */
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                inventoryViewModel.produtos,
                inventoryViewModel.history,
                shoppingListViewModel.items
            ) { produtos, history, shoppingList ->
                Triple(produtos, history, shoppingList)
            }.collect { (productList, historyList, currentShoppingList) ->
                updateInventorySummary(productList)
                updateSuggestions(productList, historyList, currentShoppingList)
            }
        }

        // Observa o orçamento e o total gasto para atualizar o resumo financeiro
        viewLifecycleOwner.lifecycleScope.launch {
            budgetViewModel.family.collect { family ->
                family?.let { updateBudgetUI(it.monthlyBudget, budgetViewModel.totalSpent.value) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            budgetViewModel.totalSpent.collect { spent ->
                budgetViewModel.family.value?.let { updateBudgetUI(it.monthlyBudget, spent) }
            }
        }
    }

    /**
     * Atualiza o resumo do inventário com o total de itens em stock
     * e o número de produtos a expirar nos próximos 7 dias.
     */
    private fun updateInventorySummary(productList: List<Product>) {
        val totalItems = productList.sumOf { it.quantidade }.toInt()
        binding.inventorySummaryTextView.text = resources.getQuantityString(
            R.plurals.inventory_summary_plural, totalItems, totalItems
        )

        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)

        // Conta os produtos cuja validade expira nos próximos 7 dias
        val expiringSoonCount = productList.count { product ->
            product.validade != null &&
                    product.validade >= currentTime &&
                    (product.validade - currentTime) <= sevenDaysInMillis
        }
        binding.expiringSummaryTextView.text = resources.getQuantityString(
            R.plurals.expiring_summary_plural, expiringSoonCount, expiringSoonCount
        )
    }

    /**
     * Gera sugestões de compra com base em dois critérios:
     * produtos a expirar em menos de 3 dias e os 5 produtos mais frequentes no histórico.
     * Remove da lista as sugestões que já constam na lista de compras atual.
     */
    private fun updateSuggestions(
        productList: List<Product>,
        historyList: List<com.example.restock.model.HistoryItem>,
        currentShoppingList: List<ShoppingListItem>
    ) {
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = TimeUnit.DAYS.toMillis(3)
        val suggestions = mutableSetOf<String>()

        // Critério 1: produtos a expirar em menos de 3 dias ou já expirados
        productList.forEach { product ->
            if (product.validade != null && (product.validade - currentTime) <= threeDaysInMillis) {
                suggestions.add(product.nome)
            }
        }

        // Critério 2: os 5 produtos mais frequentes no histórico de consumo
        val frequentItems = historyList
            .groupBy { it.produto }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        suggestions.addAll(frequentItems)

        // Remove os itens que já se encontram na lista de compras atual
        val currentShoppingItemNames = currentShoppingList.map { it.name.lowercase() }.toSet()
        val finalSuggestions = suggestions
            .filter { !currentShoppingItemNames.contains(it.lowercase()) }
            .take(5) // Apresenta no máximo 5 sugestões

        renderSuggestions(finalSuggestions)
    }

    /**
     * Apresenta as sugestões de compra como CheckBoxes dinâmicos.
     * Ao clicar numa sugestão, o item é adicionado automaticamente à lista de compras.
     */
    private fun renderSuggestions(suggestionNames: List<String>) {
        binding.suggestedItemsContainer.removeAllViews()

        if (suggestionNames.isEmpty()) {
            binding.noSuggestionsTextView.isVisible = true
        } else {
            binding.noSuggestionsTextView.isVisible = false
            suggestionNames.forEach { name ->
                val checkBox = CheckBox(requireContext()).apply {
                    text = name
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { addSuggestedItemToList(name, this) }
                }
                binding.suggestedItemsContainer.addView(checkBox)
            }
        }
    }

    /**
     * Atualiza o resumo do orçamento com o total gasto e a barra de progresso.
     * Calcula a percentagem gasta em relação ao orçamento mensal definido.
     */
    private fun updateBudgetUI(budget: Double, spent: Double) {
        binding.budgetSummaryTextView.text = getString(
            R.string.budget_summary,
            String.format("%.2f€", spent),
            String.format("%.2f€", budget)
        )
        // Calcula a percentagem gasta, evitando divisão por zero
        binding.budgetProgressBar.progress = if (budget > 0) {
            ((spent / budget) * 100).toInt()
        } else {
            0
        }
    }

    /**
     * Configura o listener de clique na fotografia de perfil
     * para navegar para o ecrã de conta do utilizador.
     */
    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToAccount())
        }
    }

    /**
     * Adiciona uma sugestão à lista de compras e remove-a imediatamente do painel de sugestões.
     * Caso não restem sugestões, apresenta a mensagem de lista vazia.
     */
    private fun addSuggestedItemToList(itemName: String, checkBox: CheckBox) {
        val newItem = ShoppingListItem(
            id = UUID.randomUUID().toString(),
            name = itemName,
            quantity = "1", // Quantidade padrão ao adicionar via sugestão
            isChecked = false
        )
        shoppingListViewModel.addItem(newItem)
        Toast.makeText(context, getString(R.string.item_added_to_list, itemName), Toast.LENGTH_SHORT).show()

        // Remove a sugestão clicada e verifica se ainda restam sugestões
        binding.suggestedItemsContainer.removeView(checkBox)
        if (binding.suggestedItemsContainer.childCount == 0) {
            binding.noSuggestionsTextView.isVisible = true
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
