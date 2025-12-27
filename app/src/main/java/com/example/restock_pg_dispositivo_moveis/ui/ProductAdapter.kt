package com.example.restock_pg_dispositivo_moveis.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.ItemProductBinding
import com.example.restock_pg_dispositivo_moveis.model.Product
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter para o RecyclerView que exibe a lista de produtos no inventário.
 * Usa ListAdapter para gerir a lista de forma eficiente e animada.
 *
 * @param onEdit Função lambda chamada quando o utilizador clica num item para editar.
 * @param onDelete Função lambda chamada quando o utilizador clica no botão de apagar.
 */
class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    /**
     * Cria uma nova ViewHolder quando o RecyclerView precisa de uma.
     * Infla o layout do item (item_product.xml).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    /**
     * Liga os dados de um produto (na posição 'position') à ViewHolder.
     */
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    /**
     * ViewHolder que mantém as referências para as Views de um item da lista.
     */
    inner class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        
        // Bloco init para configurar os click listeners.
        init {
            // Configura o clique no botão de apagar (ícone do lixo).
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(getItem(position)) // Chama a função lambda onDelete.
                }
            }
            // Configura o clique no item inteiro (para editar).
            binding.editContainer.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit(getItem(position)) // Chama a função lambda onEdit.
                }
            }
        }

        /**
         * Preenche as Views com os dados do produto.
         */
        fun bind(product: Product) {
            binding.apply {
                // Define o nome do produto.
                productNameTextView.text = product.nome
                
                // Formata o preço e define o texto da quantidade.
                val precoFormatado = String.format(Locale.getDefault(), "%.2f€", product.preco)
                productQuantityTextView.text = "Qtd: ${product.quantidade} | $precoFormatado"

                // Lógica para a data de validade.
                if (product.validade != null) {
                    val date = Date(product.validade!!)
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    productExpiryTextView.text = "Expira a: ${dateFormat.format(date)}"
                    // Define a cor do texto com base na proximidade da data de validade.
                    setValidadeAlert(productExpiryTextView, date)
                } else {
                    productExpiryTextView.text = "Sem data de validade"
                    productExpiryTextView.setTextColor(Color.GRAY)
                }

                // Carrega a imagem do produto usando o Glide.
                Glide.with(itemView.context)
                    .load(product.imagemUrl)
                    .placeholder(R.drawable.ic_restock_logo) // Imagem padrão enquanto carrega.
                    .into(productImageView)
            }
        }
    }

    /**
     * Define a cor do texto da data de validade para alertar o utilizador.
     * Vermelho: Expirado.
     * Amarelo: Expira em 7 dias ou menos.
     * Preto: Tudo ok.
     */
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

    /**
     * Callback para calcular a diferença entre duas listas e atualizar o RecyclerView de forma eficiente.
     */
    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        // Verifica se dois objetos representam o mesmo item (pelo ID).
        override fun areItemsTheSame(oldItem: Product, newItem: Product) =
            oldItem.id == newItem.id

        // Verifica se o conteúdo de dois itens é idêntico.
        override fun areContentsTheSame(oldItem: Product, newItem: Product) =
            oldItem == newItem
    }
}
