package com.example.restock.ui.family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock.databinding.ItemFamilyMemberBinding
import com.google.firebase.auth.FirebaseAuth

enum class AdapterMode {
    MEMBERS,
    PENDING
}

class FamilyMemberAdapter(
    private val mode: AdapterMode,
    private val onRemoveMember: (FamilyMember) -> Unit = {},
    private val onRoleClick: (FamilyMember) -> Unit = {},
    private val onAcceptRequest: (FamilyMember) -> Unit = {},
    private val onRejectRequest: (FamilyMember) -> Unit = {}
) : ListAdapter<FamilyMember, FamilyMemberAdapter.MemberViewHolder>(DiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var isAdmin: Boolean = false

    fun setAdminStatus(admin: Boolean) {
        val statusChanged = isAdmin != admin
        isAdmin = admin
        if (statusChanged) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MemberViewHolder(private val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: FamilyMember) {
            binding.memberNameTextView.text = member.user.name

            when (mode) {
                AdapterMode.MEMBERS -> {
                    binding.roleContainer.visibility = View.VISIBLE
                    binding.pendingActionsContainer.visibility = View.GONE
                    binding.memberRoleTextView.text = member.role

                    if (isAdmin) {
                        binding.roleEditIcon.visibility = View.VISIBLE
                        binding.roleContainer.setOnClickListener { onRoleClick(member) }
                    } else {
                        binding.roleEditIcon.visibility = View.GONE
                        binding.roleContainer.setOnClickListener(null)
                    }

                    if (isAdmin && member.user.uid != currentUserId) {
                        binding.removeMemberButton.visibility = View.VISIBLE
                        binding.removeMemberButton.setOnClickListener { onRemoveMember(member) }
                    } else {
                        binding.removeMemberButton.visibility = View.GONE
                    }
                }
                AdapterMode.PENDING -> {
                    binding.roleContainer.visibility = View.GONE
                    binding.removeMemberButton.visibility = View.GONE
                    binding.pendingActionsContainer.visibility = View.VISIBLE

                    binding.acceptButton.setOnClickListener { onAcceptRequest(member) }
                    binding.rejectButton.setOnClickListener { onRejectRequest(member) }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember) = oldItem.user.uid == newItem.user.uid
        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember) = oldItem == newItem
    }
}
