package com.example.restock.ui.gamification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock.R
import com.example.restock.databinding.ItemBadgeBinding

/** HUGO MOREIRA - a22402246 */
class BadgeAdapter : ListAdapter<Badge, BadgeAdapter.BadgeViewHolder>(BadgeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val binding = ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BadgeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BadgeViewHolder(private val binding: ItemBadgeBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(badge: Badge) {
            binding.badgeTitle.setText(badge.titleRes)
            binding.badgeDescription.setText(badge.descriptionRes)
            binding.badgeIcon.setImageResource(badge.iconRes)
            binding.badgeRequirement.setText(badge.requirementRes)

            if (badge.isUnlocked) {
                binding.badgeIconWrapper.setBackgroundResource(badge.bgRes)
                binding.badgeIcon.setColorFilter(ContextCompat.getColor(itemView.context, badge.iconTintRes))
                binding.badgeStatus.setImageResource(R.drawable.ic_check)
                binding.badgeStatus.setColorFilter(ContextCompat.getColor(itemView.context, R.color.green_500))
                binding.badgeStatus.visibility = View.VISIBLE
                binding.badgeRequirement.visibility = View.GONE
            } else {
                binding.badgeIconWrapper.setBackgroundResource(R.drawable.bg_icon_grey)
                binding.badgeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.grey_400))
                binding.badgeStatus.setImageResource(R.drawable.ic_lock)
                binding.badgeStatus.setColorFilter(ContextCompat.getColor(itemView.context, R.color.grey_400))
                binding.badgeStatus.visibility = View.VISIBLE
                binding.badgeRequirement.visibility = View.VISIBLE
            }
        }
    }

    class BadgeDiffCallback : DiffUtil.ItemCallback<Badge>() {
        override fun areItemsTheSame(oldItem: Badge, newItem: Badge) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Badge, newItem: Badge) = oldItem == newItem
    }
}
