package com.example.restock.ui.budget

// Android - para gerir o ciclo de vida, cores e vistas do fragmento
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.github.mikephil.charting.formatter.PercentFormatter

// Material Design - para apresentar o diálogo de edição do orçamento
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Firebase - autenticação para obter os dados do utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para observar os fluxos de dados do ViewModel de forma assíncrona
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pela gestão do orçamento familiar.
 * Apresenta o total gasto, a barra de progresso do orçamento mensal
 * e um gráfico circular com os gastos distribuídos por categoria.
 */
class BudgetFragment : Fragment() {

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
     * Define o furo central, percentagens e oculta a legenda e descrição.
     */
    private fun setupPieChart() {
        binding.spendingPieChart.apply {
            isDrawHoleEnabled = true
            holeRadius = 60f
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled = false
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)
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
        if (spendings.isEmpty()) {
            binding.spendingPieChart.visibility = View.GONE
            return
        }
        binding.spendingPieChart.visibility = View.VISIBLE

        // Cria as entradas do gráfico com o total gasto por categoria
        val entries = spendings.map { PieEntry(it.total.toFloat(), it.category) }
        val dataSet = PieDataSet(entries, getString(R.string.spending_categories_title))

        // Define as cores de cada fatia do gráfico
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.blue),
            ContextCompat.getColor(requireContext(), R.color.green),
            ContextCompat.getColor(requireContext(), R.color.yellow),
            ContextCompat.getColor(requireContext(), R.color.red)
        )

        dataSet.valueFormatter = PercentFormatter(binding.spendingPieChart)
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.BLACK

        // Atualiza o gráfico com os novos dados
        binding.spendingPieChart.data = PieData(dataSet)
        binding.spendingPieChart.invalidate()
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
