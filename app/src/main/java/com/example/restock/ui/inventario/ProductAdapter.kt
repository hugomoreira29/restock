package com.example.restock.ui.inventario

// Android - para cores e vistas de cada item da lista
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

// AndroidX - para o RecyclerView e cálculo eficiente de diferenças na lista
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// Glide - para carregar a imagem do produto de forma eficiente
import com.bumptech.glide.Glide

// Classes internas da aplicação - recursos, binding e modelo do produto
import com.example.restock.R
import com.example.restock.databinding.ItemProductBinding
import com.example.restock.model.Product

// Java - para formatar e comparar datas de validade
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** HUGO MOREIRA - a22402246
 * Adaptador para a lista de produtos do inventário familiar.
 * Apresenta cada produto com nome, quantidade, preço total e validade,
 * aplicando um alerta visual a vermelho ou amarelo consoante o estado de validade.
 */
class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder responsável por apresentar os dados de cada produto.
     * Configura os listeners de edição e eliminação no bloco init.
     */
    inner class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Configura o botão de eliminar com verificação de posição válida
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onDelete(getItem(position))
            }

            // Configura o contentor de edição com verificação de posição válida
            binding.editContainer.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onEdit(getItem(position))
            }
        }

        fun bind(product: Product) {
            binding.apply {
                productNameTextView.text = product.nome

                // Para unidades inteiras, apresenta sem casas decimais
                val qtdTexto = if (product.unidade == "un") {
                    "${product.quantidade.toInt()} ${product.unidade}"
                } else {
                    String.format(Locale.getDefault(), "%.2f %s", product.quantidade, product.unidade)
                }

                // Calcula e formata o preço total (quantidade × preço unitário)
                val precoTotal = product.quantidade * product.preco
                val precoFormatado = String.format(Locale.getDefault(), "%.2f€", precoTotal)
                productQuantityTextView.text = "Qtd: $qtdTexto | Total: $precoFormatado"

                // Apresenta a data de validade ou uma mensagem alternativa caso não exista
                if (product.validade != null) {
                    val date = Date(product.validade!!)
                    val daysUntilExpiration = (date.time - Date().time) / (1000 * 60 * 60 * 24)
                    
                    // Altera o prefixo conforme o produto já expirou ou não
                    val prefix = if (daysUntilExpiration < 0) "Expirou a: " else "Expira a: "
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                    
                    productExpiryTextView.text = "$prefix$dateFormatted"
                    setValidadeAlert(binding.root as MaterialCardView, productExpiryTextView, date)
                } else {
                    productExpiryTextView.text = "Sem data de validade"
                    productExpiryTextView.setTextColor(Color.GRAY)
                    (binding.root as MaterialCardView).setCardBackgroundColor(
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
                    )
                }

                // Carrega a imagem do produto com o logótipo como placeholder
                Glide.with(itemView.context)
                    .load(product.imagemUrl)
                    .placeholder(R.drawable.ic_restock_logo)
                    .into(productImageView)
            }
        }
    }

    /**
     * Define a cor do texto da validade e o fundo do card.
     * Expirado: Card avermelhado | Hoje: Texto Vermelho | Em breve: Texto Amarelo.
     */
    private fun setValidadeAlert(card: MaterialCardView, textView: TextView, validade: Date) {
        val daysUntilExpiration = (validade.time - Date().time) / (1000 * 60 * 60 * 24)

        when {
            daysUntilExpiration < 0 -> {
                // Para produtos expirados, usamos a cor cinzenta no texto sobre o fundo avermelhado
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.expired_gray))
                card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.expired_card_bg))
            }
            daysUntilExpiration == 0L -> {
                textView.setTextColor(Color.RED)
                card.setCardBackgroundColor(
                    MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                )
            }
            daysUntilExpiration <= 7 -> {
                textView.setTextColor(Color.parseColor("#FFC107"))
                card.setCardBackgroundColor(
                    MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                )
            }
            else -> {
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.green))
                card.setCardBackgroundColor(
                    MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                )
            }
        }
    }

    /**
     * Calcula as diferenças entre produtos para atualizar apenas
     * os itens que realmente mudaram, melhorando a performance.
     */
    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        // Verifica se dois itens representam o mesmo produto pelo ID
        override fun areItemsTheSame(oldItem: Product, newItem: Product) =
            oldItem.id == newItem.id

        // Verifica se o conteúdo do produto mudou
        override fun areContentsTheSame(oldItem: Product, newItem: Product) =
            oldItem == newItem
    }
}
