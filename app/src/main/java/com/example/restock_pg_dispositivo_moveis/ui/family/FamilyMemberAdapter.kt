package com.example.restock_pg_dispositivo_moveis.ui.family

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock_pg_dispositivo_moveis.databinding.ItemFamilyMemberBinding
import com.example.restock_pg_dispositivo_moveis.model.User

class FamilyMemberAdapter : ListAdapter<User, FamilyMemberAdapter.MemberViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class MemberViewHolder(private val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.memberNameTextView.text = user.name
            // TODO: A lógica de "cargo" (Administrador, Editor, etc.) será implementada no futuro.
            binding.memberRoleTextView.text = "Membro"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
