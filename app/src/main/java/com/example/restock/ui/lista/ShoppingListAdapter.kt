package com.example.restock.ui.lista

// HUGO MOREIRA - a22402246

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock.databinding.ItemShoppingListBinding
import com.example.restock.model.ShoppingListItem

/**
 * Adapter para o RecyclerView que exibe a lista de compras.
 * Usa ListAdapter para uma gestão de lista eficiente.
 *
 * @param onCheckChanged Função chamada quando o estado de um CheckBox muda.
 * @param onDelete Função chamada quando o botão de apagar é clicado.
 * @param onItemClick Função chamada quando um item da lista é clicado.
 */
class ShoppingListAdapter(
    private val onCheckChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDelete: (ShoppingListItem) -> Unit,
    private val onItemClick: (ShoppingListItem) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListAdapter.ItemViewHolder>(DiffCallback()) {

    /**
     * Cria uma nova ViewHolder, inflando o layout do item (item_shopping_list.xml).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemShoppingListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    /**
     * Liga os dados de um item à sua ViewHolder correspondente.
     */
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    /**
     * ViewHolder que representa um único item na lista.
     */
    inner class ItemViewHolder(private val binding: ItemShoppingListBinding) : RecyclerView.ViewHolder(binding.root) {
        
        // Configura os listeners de clique uma única vez, quando a ViewHolder é criada.
        init {
            // Listener para o botão de apagar.
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(getItem(position))
                }
            }
            // Listener para o CheckBox.
            binding.itemCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCheckChanged(getItem(position), isChecked)
                }
            }
            // Listener para o clique no item inteiro.
            itemView.setOnClickListener {
                 val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        /**
         * Preenche as Views do item com os dados do objeto ShoppingListItem.
         */
        fun bind(item: ShoppingListItem) {
            binding.itemNameTextView.text = item.name
            binding.itemQuantityTextView.text = item.quantity
            binding.itemCheckBox.isChecked = item.isChecked

            // Adiciona ou remove o efeito de "riscado" no texto com base no estado do CheckBox.
            if (item.isChecked) {
                binding.itemNameTextView.paintFlags = binding.itemNameTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.itemNameTextView.paintFlags = binding.itemNameTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
    }

    /**
     * DiffUtil para otimizar as atualizações do RecyclerView.
     * Ajuda o adapter a perceber exatamente o que mudou na lista (o que foi adicionado, removido ou alterado)
     * para evitar ter de redesenhar a lista inteira, melhorando a performance.
     */
    class DiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
        // Verifica se os itens são os mesmos (pelo ID).
        override fun areItemsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem) =
            oldItem.id == newItem.id

        // Verifica se o conteúdo dos itens é o mesmo.
        override fun areContentsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem) =
            oldItem == newItem
    }
}
