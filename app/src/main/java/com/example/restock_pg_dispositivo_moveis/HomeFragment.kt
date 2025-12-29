package com.example.restock_pg_dispositivo_moveis

// HUGO MOREIRA - a22402246

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentHomeBinding
import com.example.restock_pg_dispositivo_moveis.model.ShoppingListItem
import com.example.restock_pg_dispositivo_moveis.ui.budget.BudgetViewModel
import com.example.restock_pg_dispositivo_moveis.ui.inventario.InventarioViewModel
import com.example.restock_pg_dispositivo_moveis.ui.lista.ShoppingListViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Usa activityViewModels para partilhar os dados entre fragmentos
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
        setupSuggestedList()
    }

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

    private fun setupObservers() {
        // Observador para o resumo do inventário
        viewLifecycleOwner.lifecycleScope.launch {
            inventoryViewModel.produtos.collect { productList ->
                val totalItems = productList.sumOf { it.quantidade }.toInt()
                binding.inventorySummaryTextView.text = resources.getQuantityString(R.plurals.inventory_summary_plural, totalItems, totalItems)

                val currentTime = System.currentTimeMillis()
                val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
                val expiringSoonCount = productList.count { product ->
                    product.validade != null && product.validade >= currentTime && (product.validade - currentTime) <= sevenDaysInMillis
                }
                binding.expiringSummaryTextView.text = resources.getQuantityString(R.plurals.expiring_summary_plural, expiringSoonCount, expiringSoonCount)
            }
        }

        // Observador para o resumo do orçamento
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

    private fun updateBudgetUI(budget: Double, spent: Double) {
        binding.budgetSummaryTextView.text = getString(R.string.budget_summary, String.format("%.2f€", spent), String.format("%.2f€", budget))
        if (budget > 0) {
            binding.budgetProgressBar.progress = ((spent / budget) * 100).toInt()
        } else {
            binding.budgetProgressBar.progress = 0
        }
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToAccount())
        }
    }

    private fun setupSuggestedList() {
        binding.checkLeite.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_milk), it as CheckBox)
        }
        binding.checkArroz.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_rice), it as CheckBox)
        }
        binding.checkFarinha.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_flour), it as CheckBox)
        }
    }

    private fun addSuggestedItemToList(itemName: String, checkBox: CheckBox) {
        val newItem = ShoppingListItem(
            id = UUID.randomUUID().toString(),
            name = itemName,
            quantity = "1", // Quantidade padrão
            isChecked = false
        )
        shoppingListViewModel.addItem(newItem)
        
        Toast.makeText(context, getString(R.string.item_added_to_list, itemName), Toast.LENGTH_SHORT).show()
        checkBox.isEnabled = false
        checkBox.isChecked = false // Reset checkbox
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
