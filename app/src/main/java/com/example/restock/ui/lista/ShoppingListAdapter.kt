package com.example.restock.ui.lista

// Android - para aplicar o efeito de texto riscado
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup

// AndroidX - para o RecyclerView e cálculo eficiente de diferenças na lista
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// Binding e modelo do item da lista de compras
import com.example.restock.databinding.ItemShoppingListBinding
import com.example.restock.model.ShoppingListItem

/** HUGO MOREIRA - a22402246
 * Adaptador para a lista de compras da família.
 * Gere os estados de comprado/por comprar de cada item,
 * aplicando visualmente o efeito de texto riscado nos itens já comprados.
 *
 * @param onCheckChanged Invocado quando o estado do CheckBox de um item muda.
 * @param onDelete Invocado quando o botão de eliminar é clicado.
 * @param onItemClick Invocado quando o utilizador clica num item da lista.
 */
class ShoppingListAdapter(
    private val onCheckChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDelete: (ShoppingListItem) -> Unit,
    private val onItemClick: (ShoppingListItem) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListAdapter.ItemViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemShoppingListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder responsável por apresentar os dados de cada item da lista.
     * Os listeners são configurados no bloco init para evitar atribuições repetidas.
     */
    inner class ItemViewHolder(private val binding: ItemShoppingListBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Elimina o item ao clicar no botão de apagar
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onDelete(getItem(position))
            }

            // Notifica a mudança de estado quando o CheckBox é alterado
            binding.itemCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onCheckChanged(getItem(position), isChecked)
            }

            // Notifica o clique no item completo
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onItemClick(getItem(position))
            }
        }

        /**
         * Preenche as vistas com os dados do item.
         * Aplica ou remove o efeito de texto riscado consoante o estado de compra.
         */
        fun bind(item: ShoppingListItem) {
            binding.itemNameTextView.text = item.name
            binding.itemQuantityTextView.text = item.quantity

            // Evita disparar o listener ao atualizar o estado programaticamente
            binding.itemCheckBox.setOnCheckedChangeListener(null)
            binding.itemCheckBox.isChecked = item.isChecked
            binding.itemCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onCheckChanged(getItem(position), isChecked)
            }

            // Aplica texto riscado se o item já foi comprado, ou remove caso contrário
            binding.itemNameTextView.paintFlags = if (item.isChecked) {
                binding.itemNameTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.itemNameTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // Mostra quem comprou o item (só quando marcado)
            if (item.isChecked && item.boughtBy != null) {
                binding.boughtByTextView.visibility = android.view.View.VISIBLE
                binding.boughtByTextView.text = itemView.context.getString(
                    com.example.restock.R.string.bought_by, item.boughtBy
                )
            } else {
                binding.boughtByTextView.visibility = android.view.View.GONE
            }
        }
    }

    /**
     * Calcula as diferenças entre itens para atualizar apenas
     * os itens que realmente mudaram, melhorando a performance.
     */
    class DiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
        // Verifica se dois itens representam o mesmo pelo ID
        override fun areItemsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem) =
            oldItem.id == newItem.id

        // Verifica se o conteúdo do item mudou
        override fun areContentsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem) =
            oldItem == newItem
    }
}
