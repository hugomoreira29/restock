package com.example.restock

// Android - para gerir o ciclo de vida e vistas do fragmento
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast

// AndroidX - para visibilidade de vistas, ViewModels partilhados, coroutines e navegação
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

// Glide - para carregar a fotografia de perfil do utilizador
import com.bumptech.glide.Glide

// Classes internas da aplicação - binding, modelos e ViewModels
import com.example.restock.data.SettingsManager
import com.example.restock.databinding.FragmentHomeBinding
import com.example.restock.model.Product
import com.example.restock.model.ShoppingListItem
import com.example.restock.ui.budget.BudgetViewModel
import com.example.restock.ui.inventario.InventarioViewModel
import com.example.restock.ui.lista.ShoppingListViewModel

// MPAndroidChart - para o gráfico circular
import android.graphics.Color
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

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
 * produtos a expirar em breve e sugestões de compra geradas automaticamente.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModels partilhados com a Activity para aceder aos dados em tempo real
    private val inventoryViewModel: InventarioViewModel by activityViewModels()
    private val shoppingListViewModel: ShoppingListViewModel by activityViewModels()
    private val budgetViewModel: BudgetViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var settingsManager: SettingsManager
    private var snoozedSuggestions: Map<String, Long> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext().applicationContext)
        setupUserData()
        setupPieChart()
        setupObservers()
        setupClickListeners()
    }

    /**
     * Configura as propriedades visuais do gráfico circular no ecrã Home.
     */
    private fun setupPieChart() {
        binding.pieChart.apply {
            isDrawHoleEnabled = true
            holeRadius = 75f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            setDrawEntryLabels(false)
            description.isEnabled = false
            legend.isEnabled = false
            isRotationEnabled = false
            setTouchEnabled(false)
        }
    }

    /**
     * Carrega e apresenta os dados do utilizador autenticado.
     */
    private fun setupUserData() {
        val user = auth.currentUser
        if (user != null && user.displayName != null) {
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
     */
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.snoozedSuggestionsFlow.collect { snoozed ->
                snoozedSuggestions = snoozed
            }
        }

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
     * Atualiza o resumo do inventário e o gráfico circular.
     */
    private fun updateInventorySummary(productList: List<Product>) {
        val totalItems = productList.sumOf { it.quantidade }.toInt()
        binding.inventorySummaryTextView.text = resources.getQuantityString(
            R.plurals.inventory_summary_plural, totalItems, totalItems
        )

        val currentTime = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)
        val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)

        var expiredCount = 0f
        var expiresTodayCount = 0f
        var expiringSoonCount = 0f
        var goodCount = 0f

        productList.forEach { product ->
            if (product.validade == null) {
                goodCount++
            } else {
                val diff = product.validade - currentTime
                val days = diff / dayInMillis
                
                when {
                    days < 0 -> expiredCount++
                    days == 0L -> expiresTodayCount++
                    days <= 7 -> expiringSoonCount++
                    else -> goodCount++
                }
            }
        }

        binding.expiringSummaryTextView.text = resources.getQuantityString(
            R.plurals.expiring_summary_plural, (expiringSoonCount + expiresTodayCount + expiredCount).toInt(), (expiringSoonCount + expiresTodayCount + expiredCount).toInt()
        )

        updatePieChart(goodCount, expiringSoonCount, expiresTodayCount, expiredCount)
    }

    /**
     * Desenha os dados no gráfico circular com cores por estado.
     */
    private fun updatePieChart(good: Float, soon: Float, today: Float, expired: Float) {
        if (good == 0f && soon == 0f && today == 0f && expired == 0f) {
            binding.pieChart.isVisible = false
            return
        }
        binding.pieChart.isVisible = true

        val entries = ArrayList<PieEntry>()
        val sliceColors = ArrayList<Int>()

        if (good > 0) {
            entries.add(PieEntry(good))
            sliceColors.add(Color.parseColor("#FFFFFF"))
        }
        if (soon > 0) {
            entries.add(PieEntry(soon))
            sliceColors.add(Color.parseColor("#FFD54F"))
        }
        if (today > 0) {
            entries.add(PieEntry(today))
            sliceColors.add(Color.parseColor("#FF7043"))
        }
        if (expired > 0) {
            entries.add(PieEntry(expired))
            sliceColors.add(Color.parseColor("#B0BEC5"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            setDrawValues(false)
            sliceSpace = 2f
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.animateY(600)
        binding.pieChart.invalidate()
    }

    /**
     * Gera sugestões de compra dinâmicas.
     */
    private fun updateSuggestions(
        productList: List<Product>,
        historyList: List<com.example.restock.model.HistoryItem>,
        currentShoppingList: List<ShoppingListItem>
    ) {
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = TimeUnit.DAYS.toMillis(3)
        val ninetyDaysInMillis = TimeUnit.DAYS.toMillis(90)
        val ninetyDaysAgo = currentTime - ninetyDaysInMillis

        // Nomes dos produtos com stock suficiente no inventário (exclui da sugestão)
        val inStockNames = productList
            .filter { it.quantidade > 0 && (it.validade == null || it.validade > currentTime + threeDaysInMillis) }
            .map { it.nome.lowercase() }
            .toSet()

        val suggestions = mutableListOf<String>()

        // 1. Produtos quase a expirar (nos próximos 3 dias): comprar o substituto
        productList.forEach { product ->
            if (product.validade != null && (product.validade - currentTime) in 0..threeDaysInMillis) {
                suggestions.add(product.nome)
            }
        }

        // 2. Histórico dos últimos 90 dias com pontuação ponderada por frequência e recência
        val scoredItems = historyList
            .filter { it.dataConsumo >= ninetyDaysAgo }
            .groupBy { it.produto }
            .mapValues { (_, entries) ->
                // Cada compra vale mais quanto mais recente: 1.0 (hoje) a 0.0 (90 dias atrás)
                entries.sumOf { entry ->
                    val age = currentTime - entry.dataConsumo
                    maxOf(0.0, 1.0 - age.toDouble() / ninetyDaysInMillis)
                }
            }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }

        suggestions.addAll(scoredItems)

        // 3. Filtros: já na lista, em stock, ignorados temporariamente
        val shoppingNames = currentShoppingList.map { it.name.lowercase() }.toSet()
        val now = System.currentTimeMillis()
        val finalSuggestions = suggestions
            .distinctBy { it.lowercase() }
            .filter { !shoppingNames.contains(it.lowercase()) }
            .filter { !inStockNames.contains(it.lowercase()) }
            .filter { name -> (snoozedSuggestions[name] ?: 0L) <= now }
            .take(5)

        renderSuggestions(finalSuggestions)
    }

    /**
     * Apresenta as sugestões na interface com opção de ignorar temporariamente.
     */
    private fun renderSuggestions(suggestionNames: List<String>) {
        binding.suggestedItemsContainer.removeAllViews()
        if (suggestionNames.isEmpty()) {
            binding.noSuggestionsTextView.isVisible = true
        } else {
            binding.noSuggestionsTextView.isVisible = false
            suggestionNames.forEach { name ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val checkBox = CheckBox(requireContext()).apply {
                    text = name
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { addSuggestedItemToList(name, rowLayout) }
                }

                val snoozeButton = ImageButton(requireContext()).apply {
                    setImageResource(R.drawable.ic_close)
                    background = null
                    contentDescription = getString(R.string.snooze_suggestion_desc)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { showSnoozeDialog(name, rowLayout) }
                }

                rowLayout.addView(checkBox)
                rowLayout.addView(snoozeButton)
                binding.suggestedItemsContainer.addView(rowLayout)
            }
        }
    }

    /**
     * Mostra diálogo para o utilizador escolher durante quanto tempo ignorar a sugestão.
     */
    private fun showSnoozeDialog(name: String, rowView: View) {
        val options = arrayOf(
            getString(R.string.snooze_7_days),
            getString(R.string.snooze_30_days),
            getString(R.string.snooze_3_months)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.snooze_suggestion_title, name))
            .setItems(options) { _, which ->
                val days = when (which) { 0 -> 7; 1 -> 30; else -> 90 }
                viewLifecycleOwner.lifecycleScope.launch {
                    settingsManager.snoozeSuggestion(name, days)
                }
                binding.suggestedItemsContainer.removeView(rowView)
                if (binding.suggestedItemsContainer.childCount == 0)
                    binding.noSuggestionsTextView.isVisible = true
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Atualiza a barra de progresso do orçamento.
     */
    private fun updateBudgetUI(budget: Double, spent: Double) {
        binding.budgetSummaryTextView.text = getString(
            R.string.budget_summary,
            String.format("%.2f€", spent),
            String.format("%.2f€", budget)
        )
        binding.budgetProgressBar.progress = if (budget > 0) {
            ((spent / budget) * 100).toInt()
        } else {
            0
        }
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToAccount())
        }
    }

    private fun addSuggestedItemToList(itemName: String, rowView: View) {
        val newItem = ShoppingListItem(
            id = UUID.randomUUID().toString(),
            name = itemName,
            quantity = "1",
            isChecked = false
        )
        shoppingListViewModel.addItem(newItem)
        Toast.makeText(context, getString(R.string.item_added_to_list, itemName), Toast.LENGTH_SHORT).show()
        binding.suggestedItemsContainer.removeView(rowView)
        if (binding.suggestedItemsContainer.childCount == 0) {
            binding.noSuggestionsTextView.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
