package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentInventarioBinding
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.ui.ProductAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by viewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var productAdapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadUserData()
    }

    private fun loadUserData(){
        val user = auth.currentUser
        user?.let{
            val userName = it.displayName ?: ""
            binding.inventoryTitleTextView.text = "INVENTÁRIO FAMILIA ${userName.uppercase()}"
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            onDelete = { product ->
                showDeleteConfirmationDialog(product)
            },
            onEdit = { product ->
                val action = InventoryFragmentDirections.actionInventoryFragmentToAdicionarProdutoFragment(produtoId = product.id)
                findNavController().navigate(action)
            }
        )
        binding.inventarioRecyclerView.apply {
            adapter = productAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun showDeleteConfirmationDialog(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Apagar Produto")
            .setMessage("Tem a certeza que quer apagar '${product.nome}'?")
            .setNegativeButton("Não", null)
            .setPositiveButton("Sim") { _, _ ->
                viewModel.deleteProduct(product.id)
            }
            .show()
    }

    private fun setupClickListeners() {
        binding.addProductButton.setOnClickListener { // Changed from fab_add_product
            val action = InventoryFragmentDirections.actionInventoryFragmentToAdicionarProdutoFragment(null)
            findNavController().navigate(action)
        }
        binding.profileImageView.setOnClickListener{
             findNavController().navigate(InventoryFragmentDirections.actionInventoryFragmentToAccountFragment())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.produtos.collect { productList ->
                // Update RecyclerView
                productAdapter.submitList(productList)

                // Update Stats
                val totalItems = productList.sumOf { it.quantidade }.toInt()
                binding.totalItemsTextView.text = "$totalItems itens no inventário"

                val currentTime = System.currentTimeMillis()
                val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
                val expiringSoonCount = productList.count { product ->
                    product.validade != null && product.validade >= currentTime && (product.validade - currentTime) <= sevenDaysInMillis
                }
                binding.expiringItemsTextView.text = "$expiringSoonCount produtos prestes a expirar"
                
                // Update Progress Bar
                if(productList.isNotEmpty()){
                    val progress = ((productList.size - expiringSoonCount) * 100) / productList.size
                    binding.expiringProgressBar.progress = progress
                } else {
                    binding.expiringProgressBar.progress = 100
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
