package com.example.restock.ui.inventario

// HUGO MOREIRA - a22402246

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.restock.R
import com.example.restock.databinding.ItemProductBinding
import com.example.restock.model.Product
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(getItem(position))
                }
            }
            binding.editContainer.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit(getItem(position))
                }
            }
        }

        fun bind(product: Product) {
            binding.apply {
                productNameTextView.text = product.nome
                val precoFormatado = String.format(Locale.getDefault(), "%.2f€", product.preco)
                productQuantityTextView.text = "Qtd: ${product.quantidade} | $precoFormatado"

                if (product.validade != null) {
                    val date = Date(product.validade!!)
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    productExpiryTextView.text = "Expira a: ${dateFormat.format(date)}"
                    setValidadeAlert(productExpiryTextView, date)
                } else {
                    productExpiryTextView.text = "Sem data de validade"
                    productExpiryTextView.setTextColor(Color.GRAY)
                }

                Glide.with(itemView.context)
                    .load(product.imagemUrl)
                    .placeholder(R.drawable.ic_restock_logo)
                    .into(productImageView)
            }
        }
    }

    private fun setValidadeAlert(textView: TextView, validade: Date) {
        val today = Date()
        val diffTime = validade.time - today.time
        val daysUntilExpiration = diffTime / (1000 * 60 * 60 * 24)

        if (daysUntilExpiration <= 0) {
            textView.setTextColor(Color.RED)
        } else if (daysUntilExpiration <= 7) {
            textView.setTextColor(Color.parseColor("#FFC107")) // Amarelo/Âmbar
        } else {
            textView.setTextColor(Color.BLACK)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Product, newItem: Product) =
            oldItem == newItem
    }
}
