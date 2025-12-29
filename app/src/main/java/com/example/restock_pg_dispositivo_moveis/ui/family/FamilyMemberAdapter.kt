package com.example.restock_pg_dispositivo_moveis.ui.family

// HUGO MOREIRA - a22402246

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock_pg_dispositivo_moveis.databinding.ItemFamilyMemberBinding
import com.google.firebase.auth.FirebaseAuth

class FamilyMemberAdapter(
    private var isAdmin: Boolean = false, 
    private val onRemoveMember: (FamilyMember) -> Unit,
    private val onRoleClick: (FamilyMember) -> Unit
) : ListAdapter<FamilyMember, FamilyMemberAdapter.MemberViewHolder>(DiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    fun setAdminStatus(admin: Boolean) {
        val statusChanged = isAdmin != admin
        isAdmin = admin
        // Notifica o adapter para se redesenhar se o status de admin mudou
        if (statusChanged) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class MemberViewHolder(private val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: FamilyMember) {
            binding.memberNameTextView.text = member.user.name
            binding.memberRoleTextView.text = member.role

            // Controla a visibilidade da seta e o clique no container do cargo
            if (isAdmin) {
                binding.roleEditIcon.visibility = View.VISIBLE
                binding.roleContainer.setOnClickListener { onRoleClick(member) }
            } else {
                binding.roleEditIcon.visibility = View.GONE
                binding.roleContainer.setOnClickListener(null) // Remove o clique se não for admin
            }

            // Controla a visibilidade do botão de remover
            if (isAdmin && member.user.uid != currentUserId) {
                binding.removeMemberButton.visibility = View.VISIBLE
                binding.removeMemberButton.setOnClickListener { onRemoveMember(member) }
            } else {
                binding.removeMemberButton.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember) = oldItem.user.uid == newItem.user.uid
        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember) = oldItem == newItem
    }
}
