package com.example.restock.ui.gamification

// Android - para inflar e gerir as vistas de cada item da lista
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

// AndroidX - para aplicar cores e gerir o RecyclerView eficientemente
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// Classes internas da aplicação - recursos e binding
import com.example.restock.R
import com.example.restock.databinding.ItemBadgeBinding

/** HUGO MOREIRA - a22402246
 * Adaptador para a lista de emblemas de gamificação.
 * Apresenta cada emblema com título, descrição e estado visual diferenciado
 * consoante o emblema esteja desbloqueado ou ainda bloqueado.
 */
class BadgeAdapter : ListAdapter<Badge, BadgeAdapter.BadgeViewHolder>(BadgeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val binding = ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BadgeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder responsável por apresentar os dados de cada emblema.
     * Aplica estilos visuais distintos consoante o estado de desbloqueio.
     */
    class BadgeViewHolder(private val binding: ItemBadgeBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(badge: Badge) {
            binding.badgeTitle.setText(badge.titleRes)
            binding.badgeDescription.setText(badge.descriptionRes)

            if (badge.isUnlocked) {
                // Emblema desbloqueado: apresenta a cor original e o estado visível
                binding.badgeIcon.clearColorFilter()
                binding.badgeStatus.visibility = View.VISIBLE
                binding.root.alpha = 1.0f
            } else {
                // Emblema bloqueado: aplica filtro cinzento e reduz a opacidade
                binding.badgeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.grey_400))
                binding.badgeStatus.visibility = View.GONE
                binding.root.alpha = 0.6f
            }
        }
    }

    /**
     * Calcula as diferenças entre emblemas para atualizar apenas
     * os itens que realmente mudaram, melhorando a performance.
     */
    class BadgeDiffCallback : DiffUtil.ItemCallback<Badge>() {
        // Verifica se dois itens representam o mesmo emblema pelo ID
        override fun areItemsTheSame(oldItem: Badge, newItem: Badge) = oldItem.id == newItem.id

        // Verifica se o conteúdo do emblema mudou
        override fun areContentsTheSame(oldItem: Badge, newItem: Badge) = oldItem == newItem
    }
}
