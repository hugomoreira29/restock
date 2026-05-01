package com.example.restock.ui.inventario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock.R
import com.example.restock.databinding.FragmentInvoiceReviewBinding
import kotlinx.coroutines.launch

class InvoiceReviewFragment : Fragment() {

    private var _binding: FragmentInvoiceReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by activityViewModels()
    private lateinit var adapter: InvoiceReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = InvoiceReviewAdapter { count -> updateAddButton(count) }

        binding.invoiceProductsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@InvoiceReviewFragment.adapter
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.addProductsButton.setOnClickListener { addProducts() }

        observePendingProducts()
    }

    private fun observePendingProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pendingInvoiceProducts.collect { products ->
                if (products.isEmpty() && adapter.itemCount == 0) return@collect
                if (products.isNotEmpty()) {
                    adapter.setItems(products)
                }
            }
        }
    }

    private fun updateSubtitle(count: Int) {
        binding.subtitleTextView.text = getString(R.string.invoice_review_subtitle, count)
    }

    private fun updateAddButton(count: Int) {
        binding.addProductsButton.text = getString(R.string.invoice_add_button, count)
        binding.addProductsButton.isEnabled = count > 0
        updateSubtitle(count)
    }

    private fun addProducts() {
        val products = adapter.getItems()
        if (products.isEmpty()) {
            Toast.makeText(context, getString(R.string.invoice_empty), Toast.LENGTH_SHORT).show()
            return
        }

        products.forEach { viewModel.addProduct(it) }

        viewModel.clearPendingInvoiceProducts()

        Toast.makeText(
            context,
            getString(R.string.invoice_added_success, products.size),
            Toast.LENGTH_LONG
        ).show()

        findNavController().popBackStack(R.id.inventoryFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
