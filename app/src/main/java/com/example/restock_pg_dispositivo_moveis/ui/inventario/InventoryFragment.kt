package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentInventarioBinding
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.ui.ProductAdapter
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    // Usa activityViewModels() para partilhar a instância do ViewModel.
    private val viewModel: InventarioViewModel by activityViewModels()
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

        setupPieChart()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadUserData()
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            isDrawHoleEnabled = true
            holeRadius = 80f
            setDrawEntryLabels(false)
            description.isEnabled = false
            legend.isEnabled = false
            isRotationEnabled = false
            setTouchEnabled(false)
        }
    }

    private fun loadUserData(){
        val user = auth.currentUser
        user?.let{
            val userName = it.displayName ?: ""
            binding.inventoryTitleTextView.text = getString(R.string.inventory_family, userName.uppercase())
            
            Glide.with(this)
                .load(it.photoUrl)
                .placeholder(R.drawable.ic_avatar)
                .circleCrop()
                .into(binding.profileImageView)
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
            .setTitle(getString(R.string.delete_product_title))
            .setMessage(getString(R.string.delete_product_message, product.nome))
            .setNegativeButton(getString(R.string.no), null)
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.deleteProduct(product.id)
            }
            .show()
    }

    private fun setupClickListeners() {
        binding.addProductButton.setOnClickListener { 
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
                productAdapter.submitList(productList)

                val totalItems = productList.sumOf { it.quantidade }.toInt()
                binding.totalItemsTextView.text = resources.getQuantityString(R.plurals.inventory_summary_plural, totalItems, totalItems)

                updatePieChartData(productList)
            }
        }
    }

    private fun updatePieChartData(productList: List<Product>) {
        if (productList.isEmpty()) {
            binding.pieChart.visibility = View.INVISIBLE
            binding.expiringItemsTextView.text = resources.getQuantityString(R.plurals.expiring_summary_plural, 0, 0)
            return
        }
        binding.pieChart.visibility = View.VISIBLE

        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)

        val expiredCount = productList.count { it.validade != null && it.validade < currentTime }.toFloat()
        val expiringSoonCount = productList.count { it.validade != null && it.validade >= currentTime && (it.validade - currentTime) <= sevenDaysInMillis }.toFloat()
        val goodCount = (productList.size - expiredCount - expiringSoonCount).toFloat()

        val totalExpiring = (expiringSoonCount + expiredCount).toInt()
        binding.expiringItemsTextView.text = resources.getQuantityString(R.plurals.expiring_summary_plural, totalExpiring, totalExpiring)

        val entries = ArrayList<PieEntry>()
        if (goodCount > 0) entries.add(PieEntry(goodCount))
        if (expiringSoonCount > 0) entries.add(PieEntry(expiringSoonCount))
        if (expiredCount > 0) entries.add(PieEntry(expiredCount))

        val dataSet = PieDataSet(entries, "Inventory Status")
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.green),
            ContextCompat.getColor(requireContext(), R.color.yellow),
            ContextCompat.getColor(requireContext(), R.color.red)
        )
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
