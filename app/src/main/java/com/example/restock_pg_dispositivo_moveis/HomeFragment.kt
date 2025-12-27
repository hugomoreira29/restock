package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentHomeBinding
import com.example.restock_pg_dispositivo_moveis.model.ShoppingListItem
import com.example.restock_pg_dispositivo_moveis.ui.inventario.InventarioViewModel
import com.example.restock_pg_dispositivo_moveis.ui.lista.ShoppingListViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val inventoryViewModel: InventarioViewModel by activityViewModels()
    private val shoppingListViewModel: ShoppingListViewModel by viewModels()
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
        setupInventorySummary()
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

    private fun setupInventorySummary() {
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
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_account)
        }
    }

    private fun setupSuggestedList() {
        binding.checkLeite.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_milk), binding.checkLeite)
        }
        binding.checkArroz.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_rice), binding.checkArroz)
        }
        binding.checkFarinha.setOnClickListener {
            addSuggestedItemToList(getString(R.string.suggested_flour), binding.checkFarinha)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
