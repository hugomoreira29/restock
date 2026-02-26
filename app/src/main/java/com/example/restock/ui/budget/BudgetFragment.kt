package com.example.restock.ui.budget

// HUGO MOREIRA - a22402246

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.restock.R
import com.example.restock.databinding.FragmentBudgetBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!

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

    private fun updateBudgetUI(budget: Double, spent: Double) {
        binding.budgetTextView.text = getString(R.string.budget_summary, String.format("%.2f€", spent), String.format("%.2f€", budget))
        if (budget > 0) {
            binding.budgetProgressBar.progress = ((spent / budget) * 100).toInt()
        } else {
            binding.budgetProgressBar.progress = 0
        }
    }

    private fun updatePieChartData(spendings: List<CategorySpending>) {
        if (spendings.isEmpty()) {
            binding.spendingPieChart.visibility = View.GONE
            return
        }
        binding.spendingPieChart.visibility = View.VISIBLE

        val entries = spendings.map { PieEntry(it.total.toFloat(), it.category) }
        val dataSet = PieDataSet(entries, getString(R.string.spending_categories_title))

        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.blue),
            ContextCompat.getColor(requireContext(), R.color.green),
            ContextCompat.getColor(requireContext(), R.color.yellow),
            ContextCompat.getColor(requireContext(), R.color.red)
        )

        dataSet.valueFormatter = PercentFormatter(binding.spendingPieChart)
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.BLACK

        val data = PieData(dataSet)
        binding.spendingPieChart.data = data
        binding.spendingPieChart.invalidate()
    }

    private fun setupClickListeners() {
        binding.editBudgetButton.setOnClickListener {
            showEditBudgetDialog()
        }
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(BudgetFragmentDirections.actionBudgetFragmentToAccountFragment())
        }
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
