package com.example.restock.ui.budget

// Android - para gerir o ciclo de vida, cores e vistas do fragmento
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

// AndroidX - para aceder ao ViewModel, coroutines e navegação
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

// Glide - para carregar a fotografia de perfil do utilizador
import com.bumptech.glide.Glide

// Classes internas da aplicação - recursos e binding
import com.example.restock.R
import com.example.restock.databinding.FragmentBudgetBinding

// MPAndroidChart - para construir e apresentar o gráfico circular de gastos
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

// Material Design - para apresentar o diálogo de edição do orçamento
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Firebase - autenticação para obter os dados do utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Classes internas - modelo de histórico
import com.example.restock.model.BudgetSnapshot

// Coroutines - para observar os fluxos de dados do ViewModel de forma assíncrona
import kotlinx.coroutines.launch

// Java - para formatar o nome do mês
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pela gestão do orçamento familiar.
 * Apresenta o total gasto, a barra de progresso do orçamento mensal
 * e um gráfico circular com os gastos distribuídos por categoria.
 */
class BudgetFragment : Fragment() {

    private val chartColors = listOf(
        Color.parseColor("#2E7D60"),  // Esmeralda (primary)
        Color.parseColor("#D4762A"),  // Âmbar (secondary)
        Color.parseColor("#7B5EA7"),  // Violeta (tertiary)
        Color.parseColor("#1565C0"),  // Azul
        Color.parseColor("#C2185B"),  // Rosa/Vermelho
        Color.parseColor("#00838F"),  // Teal
        Color.parseColor("#E64A19"),  // Laranja escuro
        Color.parseColor("#558B2F"),  // Verde oliva
    )

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!

    // ViewModel partilhado entre fragmentos para gerir os dados do orçamento
    private val viewModel: BudgetViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPieChart()
        observeViewModel()
        setupClickListeners()
        loadUserData()
    }

    /**
     * Carrega a fotografia de perfil do utilizador autenticado.
     * Caso não tenha fotografia, apresenta o avatar por defeito.
     */
    private fun loadUserData() {
        val user = auth.currentUser
        user?.let {
            if (it.photoUrl != null) {
                Glide.with(this)
                    .load(it.photoUrl)
                    .placeholder(R.drawable.ic_avatar)
                    .circleCrop()
                    .into(binding.profileImageView)
            } else {
                binding.profileImageView.setImageResource(R.drawable.ic_avatar)
            }
        }
    }

    /**
     * Configura as propriedades visuais do gráfico circular.
     */
    private fun setupPieChart() {
        binding.spendingPieChart.apply {
            isDrawHoleEnabled = true
            holeRadius = 58f
            transparentCircleRadius = 62f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(40)
            setUsePercentValues(false)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setTouchEnabled(false)
            setCenterTextSize(15f)
            setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.primary_dark))
        }
    }

    /**
     * Observa os fluxos de dados do ViewModel e atualiza a interface
     * sempre que os dados da família, gastos totais ou gastos por categoria mudam.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.family.collect { family ->
                family?.let { updateBudgetUI(it.monthlyBudget, viewModel.totalSpent.value) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalSpent.collect { spent ->
                viewModel.family.value?.let { updateBudgetUI(it.monthlyBudget, spent) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorySpending.collect { spendings ->
                updatePieChartData(spendings)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.budgetHistory.collect { history ->
                updateBudgetHistory(history)
            }
        }
    }

    /**
     * Atualiza o texto e a barra de progresso do orçamento mensal.
     * Calcula a percentagem gasta em relação ao orçamento definido.
     */
    private fun updateBudgetUI(budget: Double, spent: Double) {
        binding.budgetTextView.text = getString(R.string.budget_summary,
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
     * Atualiza os dados do gráfico circular com os gastos por categoria.
     * Oculta o gráfico caso não existam dados para apresentar.
     */
    private fun updatePieChartData(spendings: List<CategorySpending>) {
        binding.legendContainer.removeAllViews()

        if (spendings.isEmpty()) {
            binding.spendingPieChart.visibility = View.GONE
            return
        }
        binding.spendingPieChart.visibility = View.VISIBLE

        val entries = spendings.map { PieEntry(it.total.toFloat()) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = spendings.mapIndexed { i, _ -> chartColors[i % chartColors.size] }
            sliceSpace = 3f
            selectionShift = 6f
            setDrawValues(false)
        }

        val total = spendings.sumOf { it.total }
        binding.spendingPieChart.apply {
            data = PieData(dataSet)
            setCenterText("%.2f€".format(total))
            animateY(700)
        }

        // Preenche a legenda com cor + categoria + valor
        spendings.forEachIndexed { index, spending ->
            val color = chartColors[index % chartColors.size]
            binding.legendContainer.addView(buildLegendRow(spending.category, spending.total, color))
        }
    }

    /**
     * Renderiza o histórico de orçamentos mensais, excluindo o mês atual
     * (já representado no card principal).
     */
    private fun updateBudgetHistory(history: List<BudgetSnapshot>) {
        binding.budgetHistoryContainer.removeAllViews()

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        // Gera os últimos 6 meses (sem o mês atual), preenchendo com zeros se não houver dados
        val lastSixMonths = (1..6).map { monthsAgo ->
            val target = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
            val y = target.get(Calendar.YEAR)
            val m = target.get(Calendar.MONTH) + 1
            history.find { it.year == y && it.month == m }
                ?: BudgetSnapshot(year = y, month = m, budget = 0.0, spent = 0.0)
        }

        binding.noHistoryTextView.visibility = View.GONE
        lastSixMonths.forEach { snapshot ->
            binding.budgetHistoryContainer.addView(buildHistoryRow(snapshot))
        }
    }

    private fun buildHistoryRow(snapshot: BudgetSnapshot): LinearLayout {
        val dp = resources.displayMetrics.density

        // Formata o nome do mês no idioma atual (ex: "maio 2025" ou "May 2025")
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, snapshot.year)
            set(Calendar.MONTH, snapshot.month - 1)
        }
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            .replaceFirstChar { it.uppercaseChar() }

        val percentSpent = if (snapshot.budget > 0)
            ((snapshot.spent / snapshot.budget) * 100).toInt().coerceIn(0, 100)
        else 0

        val rowColor = when {
            percentSpent >= 100 -> Color.parseColor("#C2185B")
            percentSpent >= 80  -> Color.parseColor("#D4762A")
            else                -> Color.parseColor("#2E7D60")
        }

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val vPad = (8 * dp).toInt()
            setPadding(0, vPad, 0, vPad)

            // Linha superior: mês + valor
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = monthLabel
                    textSize = 13f
                    setTextColor(Color.parseColor("#555555"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })

                addView(TextView(context).apply {
                    text = "%.2f€ / %.2f€".format(snapshot.spent, snapshot.budget)
                    textSize = 12f
                    setTextColor(rowColor)
                })
            })

            // Barra de progresso
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (6 * dp).toInt()
                ).also { it.topMargin = (4 * dp).toInt() }
                max = 100
                progress = percentSpent
                progressTintList = android.content.res.ColorStateList.valueOf(rowColor)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#1A000000")
                )
            })
        }
    }

    private fun buildLegendRow(category: String, total: Double, color: Int): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = (7 * dp).toInt()
            setPadding(0, vPad, 0, vPad)

            // Círculo colorido
            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (12 * dp).toInt(), (12 * dp).toInt()
                ).also { it.marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            })

            // Nome da categoria
            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = category
                textSize = 13f
                setTextColor(Color.parseColor("#555555"))
            })

            // Valor gasto
            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = "%.2f€".format(total)
                textSize = 13f
                setTextColor(color)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }
    }

    /**
     * Configura os listeners de clique do fragmento.
     * Trata da edição do orçamento e da navegação para o perfil.
     */
    private fun setupClickListeners() {
        binding.editBudgetButton.setOnClickListener { showEditBudgetDialog() }

        // Navega para o ecrã de conta ao clicar na fotografia de perfil
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(BudgetFragmentDirections.actionBudgetFragmentToAccountFragment())
        }
    }

    /**
     * Apresenta um diálogo para o utilizador definir um novo orçamento mensal.
     * Valida o valor introduzido antes de o guardar no ViewModel.
     */
    private fun showEditBudgetDialog() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.new_budget_hint)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_budget_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newBudgetValue = input.text.toString().toDoubleOrNull()
                if (newBudgetValue != null && newBudgetValue >= 0) {
                    viewModel.updateMonthlyBudget(newBudgetValue)
                    Toast.makeText(context, getString(R.string.budget_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, getString(R.string.invalid_value), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
