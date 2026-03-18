package com.example.restock.ui.family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.restock.databinding.ItemFamilyMemberBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Define o modo de funcionamento do adaptador.
 * MEMBERS - apresenta os membros ativos com opções de cargo e remoção.
 * PENDING - apresenta os pedidos de adesão com opções de aceitar ou rejeitar.
 */
enum class AdapterMode {
    MEMBERS,
    PENDING
}

/** HUGO MOREIRA - a22402246
 * Adaptador para a lista de membros da família.
 * Suporta dois modos distintos através de [AdapterMode]:
 * membros ativos e pedidos de adesão pendentes.
 */
class FamilyMemberAdapter(
    private val mode: AdapterMode,
    private val onRemoveMember: (FamilyMember) -> Unit = {},
    private val onRoleClick: (FamilyMember) -> Unit = {},
    private val onAcceptRequest: (FamilyMember) -> Unit = {},
    private val onRejectRequest: (FamilyMember) -> Unit = {}
) : ListAdapter<FamilyMember, FamilyMemberAdapter.MemberViewHolder>(DiffCallback()) {

    // UID do utilizador autenticado para impedir que se remova a si próprio
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Estado de administrador que controla a visibilidade das ações restritas
    private var isAdmin: Boolean = false

    /**
     * Atualiza o estado de administrador e notifica o adaptador
     * para redesenhar os itens apenas se o estado tiver mudado.
     */
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

    /**
     * ViewHolder responsável por apresentar os dados de cada membro.
     * O comportamento muda consoante o modo do adaptador.
     */
    inner class MemberViewHolder(private val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: FamilyMember) {
            binding.memberNameTextView.text = member.user.name

            when (mode) {
                AdapterMode.MEMBERS -> {
                    // Modo de membros ativos: mostra cargo e opções de gestão
                    binding.roleContainer.visibility = View.VISIBLE
                    binding.pendingActionsContainer.visibility = View.GONE
                    binding.memberRoleTextView.text = member.role

                    // Só mostra o ícone de edição de cargo se o utilizador for administrador
                    if (isAdmin) {
                        binding.roleEditIcon.visibility = View.VISIBLE
                        binding.roleContainer.setOnClickListener { onRoleClick(member) }
                    } else {
                        binding.roleEditIcon.visibility = View.GONE
                        binding.roleContainer.setOnClickListener(null)
                    }

                    // Só mostra o botão de remover se for administrador e não for o próprio utilizador
                    if (isAdmin && member.user.uid != currentUserId) {
                        binding.removeMemberButton.visibility = View.VISIBLE
                        binding.removeMemberButton.setOnClickListener { onRemoveMember(member) }
                    } else {
                        binding.removeMemberButton.visibility = View.GONE
                    }
                }

                AdapterMode.PENDING -> {
                    // Modo de pedidos pendentes: mostra apenas as ações de aceitar e rejeitar
                    binding.roleContainer.visibility = View.GONE
                    binding.removeMemberButton.visibility = View.GONE
                    binding.pendingActionsContainer.visibility = View.VISIBLE

                    binding.acceptButton.setOnClickListener { onAcceptRequest(member) }
                    binding.rejectButton.setOnClickListener { onRejectRequest(member) }
                }
            }
        }
    }

    /**
     * Calcula as diferenças entre itens da lista para atualizar
     * apenas os itens que realmente mudaram, melhorando a performance.
     */
    class DiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        // Verifica se dois itens representam o mesmo membro pelo UID
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember) =
            oldItem.user.uid == newItem.user.uid

        // Verifica se o conteúdo do membro mudou
        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember) =
            oldItem == newItem
    }
}