package com.example.restock.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock.databinding.FragmentFamilyBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FamilyViewModel by viewModels()
    private lateinit var memberAdapter: FamilyMemberAdapter
    private lateinit var pendingAdapter: FamilyMemberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        memberAdapter = FamilyMemberAdapter(
            mode = AdapterMode.MEMBERS,
            onRemoveMember = { member -> showRemoveMemberDialog(member) },
            onRoleClick = { member ->
                if (viewModel.isAdmin.value) showChangeRoleDialog(member)
            }
        )

        pendingAdapter = FamilyMemberAdapter(
            mode = AdapterMode.PENDING,
            onAcceptRequest = { pendingMember ->
                viewModel.approveJoinRequest(
                    pendingMember.user,
                    onSuccess = { Toast.makeText(context, "${pendingMember.user.name} foi adicionado à família.", Toast.LENGTH_SHORT).show() },
                    onError = { error -> Toast.makeText(context, "Erro: $error", Toast.LENGTH_LONG).show() }
                )
            },
            onRejectRequest = { pendingMember ->
                viewModel.rejectJoinRequest(
                    pendingMember.user,
                    onSuccess = { Toast.makeText(context, "Pedido de ${pendingMember.user.name} rejeitado.", Toast.LENGTH_SHORT).show() },
                    onError = { error -> Toast.makeText(context, "Erro: $error", Toast.LENGTH_LONG).show() }
                )
            }
        )
        
        binding.familyMembersRecyclerView.apply {
            adapter = memberAdapter
            layoutManager = LinearLayoutManager(context)
        }

        binding.pendingMembersRecyclerView.apply {
            adapter = pendingAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun showRemoveMemberDialog(member: FamilyMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover Membro")
            .setMessage("Tem a certeza que deseja remover '${member.user.name}' da família?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ ->
                viewModel.removeMember(
                    member.user,
                    onSuccess = { Toast.makeText(context, "${member.user.name} removido com sucesso.", Toast.LENGTH_SHORT).show() },
                    onError = { error -> Toast.makeText(context, "Erro: $error", Toast.LENGTH_LONG).show() }
                )
            }
            .show()
    }

    private fun showChangeRoleDialog(member: FamilyMember) {
        val roles = arrayOf("Admin", "Editor", "Leitor")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alterar Cargo de ${member.user.name}")
            .setItems(roles) { _, which ->
                val selectedRole = roles[which]
                viewModel.updateMemberRole(member.user.uid, selectedRole)
                Toast.makeText(context, "Cargo atualizado para $selectedRole", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.addMemberButton.setOnClickListener {
            viewModel.generateInviteCode { code ->
                showInviteCodeDialog(code)
            }
        }

        binding.joinFamilyButton.setOnClickListener {
            val code = binding.joinFamilyCodeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                viewModel.requestToJoinFamily(code,
                    onSuccess = { Toast.makeText(context, "Pedido para entrar na família enviado com sucesso!", Toast.LENGTH_LONG).show() },
                    onError = { errorMsg -> Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show() }
                )
            } else {
                Toast.makeText(context, "Insira um código de convite válido.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editFamilyNameButton.setOnClickListener {
            val currentName = binding.familyNameTextView.text.toString()
            showEditFamilyNameDialog(currentName)
        }

        binding.leaveFamilyButton.setOnClickListener {
             val currentUserMember = memberAdapter.currentList.find { it.user.uid == FirebaseAuth.getInstance().currentUser?.uid }
             if (currentUserMember?.role == "Admin" && memberAdapter.currentList.size > 1) {
                 MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ação Negada")
                    .setMessage("Você é o último Admin. Promova outro membro a 'Admin' antes de sair.")
                    .setPositiveButton("OK", null)
                    .show()
                 return@setOnClickListener
             }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sair da Família")
                .setMessage("Tem a certeza que deseja sair desta família?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair") { _, _ ->
                    currentUserMember?.let { 
                        viewModel.leaveFamily(it.user)
                    }
                }
                .show()
        }

        binding.createFamilyButton.setOnClickListener {
            showCreateFamilyDialog()
        }
    }
    
    private fun showCreateFamilyDialog() {
        val input = EditText(requireContext()).apply { hint = "Nome da Família" }
        val container = FrameLayout(requireContext()).apply {
             val margin = (16 * resources.displayMetrics.density).toInt()
             val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                 leftMargin = margin
                 rightMargin = margin
             }
             addView(input, params)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Criar Nova Família")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createFamily(name)
                    Toast.makeText(context, "Família '$name' criada!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "O nome não pode estar vazio.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showEditFamilyNameDialog(currentName: String) {
        val input = EditText(requireContext()).apply { setText(currentName) }
        val container = FrameLayout(requireContext()).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = margin
                rightMargin = margin
            }
            addView(input, params)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alterar Nome da Família")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.updateFamilyName(newName)
                    Toast.makeText(context, "Nome atualizado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "O nome não pode estar vazio.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.family.collect { family ->
                binding.familyDetailsView.isVisible = family != null
                binding.emptyStateView.isVisible = family == null
                
                family?.let { binding.familyNameTextView.text = it.name }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.members.collect { members ->
                memberAdapter.submitList(members)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val isAdmin = members.any { it.user.uid == currentUserId && it.role == "Admin" }
                
                viewModel.setIsAdmin(isAdmin)
                memberAdapter.setAdminStatus(isAdmin)
                pendingAdapter.setAdminStatus(isAdmin)
                binding.editFamilyNameButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pendingMembers.collect { pending ->
                pendingAdapter.submitList(pending)
                binding.pendingRequestsCard.isVisible = viewModel.isAdmin.value && pending.isNotEmpty()
            }
        }
    }

    private fun showInviteCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Código de Convite")
            .setMessage("Partilhe este código com quem quer convidar para a sua família:\n\n$code")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
