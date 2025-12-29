package com.example.restock_pg_dispositivo_moveis.ui.family

// HUGO MOREIRA - a22402246

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentFamilyBinding
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FamilyViewModel by viewModels()
    private lateinit var memberAdapter: FamilyMemberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // Inicializa o adapter. O estado de admin será atualizado depois.
        memberAdapter = FamilyMemberAdapter(
            isAdmin = false,
            onRemoveMember = { member ->
                showRemoveMemberDialog(member)
            },
            onRoleClick = { member ->
                // Verifica se é admin antes de abrir o diálogo (extra segurança)
                if (memberAdapter.currentList.find { it.user.uid == FirebaseAuth.getInstance().currentUser?.uid }?.role == "Admin") {
                     showChangeRoleDialog(member)
                }
            }
        )
        
        binding.familyMembersRecyclerView.apply {
            adapter = memberAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun showRemoveMemberDialog(member: FamilyMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover Membro")
            .setMessage("Tem a certeza que deseja remover '${member.user.name}' da família?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ ->
                viewModel.removeMember(member.user)
                Toast.makeText(context, "${member.user.name} removido.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showChangeRoleDialog(member: FamilyMember) {
        val roles = arrayOf("Admin", "Editor", "Leitor", "Membro")
        
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
                viewModel.joinFamily(code,
                    onSuccess = {
                        Toast.makeText(context, "Entrou na família com sucesso!", Toast.LENGTH_SHORT).show()
                        binding.joinFamilyCodeEditText.text?.clear()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(context, "Insira um código de convite válido.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editFamilyNameButton.setOnClickListener {
            val currentName = binding.familyNameTextView.text.toString()
            showEditFamilyNameDialog(currentName)
        }

        // Listener para o botão Sair da Família
        binding.leaveFamilyButton.setOnClickListener {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val currentUserMember = memberAdapter.currentList.find { it.user.uid == currentUserId }

            // Verificação de segurança: Se for ADMIN, impede a saída direta
            if (currentUserMember?.role == "Admin") {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ação Negada")
                    .setMessage("O Administrador não pode sair da família.\n\nPromova outro membro a 'Admin' antes de sair, ou apague a família se for o único membro.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // Se não for Admin, prossegue com o diálogo de confirmação
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sair da Família")
                .setMessage("Tem a certeza que deseja sair desta família? Ficará sem acesso aos produtos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair") { _, _ ->
                    if (currentUserMember != null) {
                        viewModel.removeMember(currentUserMember.user)
                        Toast.makeText(context, "Você saiu da família.", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    } else {
                        Toast.makeText(context, "Erro ao identificar utilizador.", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    private fun showEditFamilyNameDialog(currentName: String) {
        val input = EditText(requireContext())
        input.setText(currentName)
        
        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (16 * resources.displayMetrics.density).toInt()
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        container.addView(input)

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
                family?.let { binding.familyNameTextView.text = it.name }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.members.collect { members ->
                memberAdapter.submitList(members)

                // Verifica se o utilizador atual é Admin
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val currentUserMember = members.find { it.user.uid == currentUserId }
                // Considera admin se o cargo for "Admin" (ou "Administrador")
                val isAdmin = currentUserMember?.role == "Admin" || currentUserMember?.role == "Administrador"
                
                // Atualiza o adapter para mostrar/esconder o botão de apagar
                memberAdapter.setAdminStatus(isAdmin)

                // Mostra/Esconde o botão de editar nome da família
                binding.editFamilyNameButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
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
