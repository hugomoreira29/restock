package com.example.restock_pg_dispositivo_moveis.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.model.Product
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductAdapter(
    private var productList: List<Product>,
    // Usamos dois callbacks distintos para Update (clique no item) e Delete (clique no ícone)
    private val onEdit: (Product) -> Unit, // Para navegação para edição/detalhe (CRUD: Update)
    private val onDelete: (Product) -> Unit // Para a ação de apagar (CRUD: Delete)
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Campos existentes
        val name: TextView = itemView.findViewById(R.id.text_product_name) // Renomeado para clareza
        val quantityPrice: TextView = itemView.findViewById(R.id.text_product_quantity_price) // Novo campo para Qtd/Preço

        // Novos Campos para Requisitos
        val validade: TextView = itemView.findViewById(R.id.text_product_validade) // Data de validade
        val deleteButton: ImageView = itemView.findViewById(R.id.btn_delete) // Ícone de apagar

        // O item inteiro é usado para o clique de edição
        val editContainer: View = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            // Assumindo que o layout foi atualizado para item_product.xml (ConstraintLayout)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        // 1. Apresentação dos Dados
        holder.name.text = product.nome

        // Combinação de Quantidade e Preço
        val precoFormatado = String.format(Locale.getDefault(), "%.2f€", product.preco)
        holder.quantityPrice.text = "Qtd: ${product.quantidade} | $precoFormatado"

        // 2. Apresentação da Validade e Alerta (RF07)
        if (product.validade != null) {
            val date = Date(product.validade)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            holder.validade.text = "Expira a: ${dateFormat.format(date)}"

            // Lógica do Alerta Visual (RF07)
            setValidadeAlert(holder.validade, date)

        } else {
            holder.validade.text = "Sem data de validade"
            holder.validade.setTextColor(Color.GRAY)
        }

        // 3. Ações CRUD

        // Ação de EDIÇÃO/DETALHE (CRUD - UPDATE)
        holder.editContainer.setOnClickListener {
            onEdit(product)
        }

        // Ação de APAGAR (CRUD - DELETE)
        holder.deleteButton.setOnClickListener {
            onDelete(product)
        }
    }

    override fun getItemCount(): Int = productList.size

    // Função para atualização (Mantida do seu código - Boa prática!)
    fun updateList(newList: List<Product>) {
        // Idealmente, use DiffUtil para uma atualização mais eficiente
        productList = newList
        notifyDataSetChanged()
    }

    /**
     * Lógica para o Requisito Funcional RF07: Alertas de Validade.
     */
    private fun setValidadeAlert(textView: TextView, validade: Date) {
        val today = Date()
        val diffTime = validade.time - today.time
        // Converte milissegundos para dias
        val daysUntilExpiration = diffTime / (1000 * 60 * 60 * 24)

        // Regras de Alerta:
        if (daysUntilExpiration <= 0) {
            textView.setTextColor(Color.RED)
            textView.text = "EXPIRADO! (${textView.text})"
        } else if (daysUntilExpiration <= 7) {
            textView.setTextColor(Color.parseColor("#FFC107")) // Amarelo/Âmbar
        } else {
            textView.setTextColor(Color.BLACK)
        }
    }
}