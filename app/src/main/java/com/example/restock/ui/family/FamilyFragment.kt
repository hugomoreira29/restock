package com.example.restock.ui.family

// Android - para gerir o ciclo de vida e vistas do fragmento
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

// AndroidX - para visibilidade de vistas, ViewModel, coroutines e navegação
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.FragmentFamilyBinding

// Material Design - para apresentar diálogos de confirmação e edição
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Firebase - autenticação para identificar o utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para observar os fluxos de dados do ViewModel
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pela gestão da família do utilizador.
 * Permite criar ou entrar numa família através de código de convite,
 * gerir membros, alterar cargos e tratar pedidos de adesão pendentes.
 */
class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FamilyViewModel by viewModels()

    // Adaptadores para a lista de membros ativos e pedidos pendentes
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

    /**
     * Inicializa os adaptadores e configura os RecyclerViews
     * para a lista de membros e pedidos de adesão pendentes.
     */
    private fun setupRecyclerViews() {

        // Adaptador para os membros ativos da família
        memberAdapter = FamilyMemberAdapter(
            mode = AdapterMode.MEMBERS,
            onRemoveMember = { member -> showRemoveMemberDialog(member) },
            onRoleClick = { member ->
                // Só permite alterar cargos se o utilizador for administrador
                if (viewModel.isAdmin.value) showChangeRoleDialog(member)
            }
        )

        // Adaptador para os pedidos de adesão pendentes
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

    /**
     * Apresenta um diálogo de confirmação antes de remover um membro da família.
     */
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

    /**
     * Apresenta um diálogo para alterar o cargo de um membro da família.
     * Apenas disponível para administradores.
     */
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

    /**
     * Configura todos os listeners de clique do fragmento.
     * Trata da navegação, criação de família, convites, adesão e saída da família.
     */
    private fun setupClickListeners() {

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Gera um código de convite e apresenta-o num diálogo
        binding.addMemberButton.setOnClickListener {
            viewModel.generateInviteCode { code ->
                showInviteCodeDialog(code)
            }
        }

        // Envia um pedido para entrar numa família com o código introduzido
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
            showEditFamilyNameDialog(binding.familyNameTextView.text.toString())
        }

        // Verifica se o utilizador é o último administrador antes de permitir sair da família
        binding.leaveFamilyButton.setOnClickListener {
            val currentUserMember = memberAdapter.currentList.find {
                it.user.uid == FirebaseAuth.getInstance().currentUser?.uid
            }

            // Impede a saída se o utilizador for o único administrador
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
                    currentUserMember?.let { viewModel.leaveFamily(it.user) }
                }
                .show()
        }

        binding.createFamilyButton.setOnClickListener { showCreateFamilyDialog() }
    }

    /**
     * Apresenta um diálogo para criar uma nova família com o nome introduzido.
     */
    private fun showCreateFamilyDialog() {
        val input = EditText(requireContext()).apply { hint = "Nome da Família" }
        val container = FrameLayout(requireContext()).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
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

    /**
     * Apresenta um diálogo para editar o nome atual da família.
     */
    private fun showEditFamilyNameDialog(currentName: String) {
        val input = EditText(requireContext()).apply { setText(currentName) }
        val container = FrameLayout(requireContext()).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
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

    /**
     * Observa os fluxos de dados do ViewModel e atualiza a interface
     * consoante o estado da família, membros e pedidos pendentes.
     */
    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.family.collect { family ->
                // Mostra os detalhes da família ou o ecrã vazio consoante o estado
                binding.familyDetailsView.isVisible = family != null
                binding.emptyStateView.isVisible = family == null
                family?.let { binding.familyNameTextView.text = it.name }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.members.collect { members ->
                memberAdapter.submitList(members)

                // Verifica se o utilizador atual é administrador da família
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val isAdmin = members.any { it.user.uid == currentUserId && it.role == "Admin" }

                // Atualiza o estado de administrador nos adaptadores e no ViewModel
                viewModel.setIsAdmin(isAdmin)
                memberAdapter.setAdminStatus(isAdmin)
                pendingAdapter.setAdminStatus(isAdmin)

                // Só mostra o botão de editar nome aos administradores
                binding.editFamilyNameButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pendingMembers.collect { pending ->
                pendingAdapter.submitList(pending)
                // Só mostra os pedidos pendentes se o utilizador for administrador
                binding.pendingRequestsCard.isVisible = viewModel.isAdmin.value && pending.isNotEmpty()
            }
        }
    }

    /**
     * Apresenta um diálogo com o código de convite gerado para partilhar com outros utilizadores.
     */
    private fun showInviteCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Código de Convite")
            .setMessage("Partilhe este código com quem quer convidar para a sua família:\n\n$code")
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
