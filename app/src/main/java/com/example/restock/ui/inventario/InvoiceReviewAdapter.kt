package com.example.restock.ui.inventario

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.restock.databinding.ItemInvoiceProductBinding
import com.example.restock.model.Product
import java.util.Locale

class InvoiceReviewAdapter(
    private val onCountChanged: (Int) -> Unit
) : RecyclerView.Adapter<InvoiceReviewAdapter.ViewHolder>() {

    private val items = mutableListOf<Product>()

    fun setItems(products: List<Product>) {
        items.clear()
        items.addAll(products)
        notifyDataSetChanged()
        onCountChanged(items.size)
    }

    fun getItems(): List<Product> = items.toList()

    inner class ViewHolder(private val binding: ItemInvoiceProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.productNameTextView.text = product.nome
            binding.productDetailsTextView.text = buildString {
                val qty = if (product.quantidade == product.quantidade.toLong().toDouble())
                    product.quantidade.toLong().toString()
                else
                    product.quantidade.toString()
                append("$qty ${product.unidade}")
                if (product.preco > 0) append(" • ${String.format(Locale.getDefault(), "%.2f", product.preco)}€")
                if (product.categoria.isNotEmpty()) append(" • ${CategoryUtils.localize(product.categoria, itemView.context)}")
            }

            binding.deleteButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                    onCountChanged(items.size)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
