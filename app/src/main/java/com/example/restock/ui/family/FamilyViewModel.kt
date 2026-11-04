package com.example.restock.ui.family

// Android - para registar erros
import android.util.Log

// AndroidX - ViewModel para gerir os dados do ecrã de família
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Classes internas da aplicação - repositórios e modelos
import com.example.restock.data.AccountDeletionRepository
import com.example.restock.data.FamilyRepository
import com.example.restock.data.UserRepository
import com.example.restock.model.Family
import com.example.restock.model.User

// Firebase - autenticação, base de dados e operações em array
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

// Coroutines - para operações assíncronas e para expor os dados à interface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Representa um membro da família com os seus dados de utilizador e cargo.
 */
data class FamilyMember(
    val user: User,
    val role: String
)

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica de gestão da família.
 * Observa em tempo real os dados da família no Firestore e expõe
 * fluxos de dados para a interface sobre membros, pedidos e cargos.
 */
class FamilyViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val familyRepository = FamilyRepository(db)
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    // Fluxos de dados expostos à interface para observação
    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family

    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members

    private val _pendingMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val pendingMembers: StateFlow<List<FamilyMember>> = _pendingMembers

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    // ID da família atual, usado para todas as operações no Firestore
    private var familyId: String? = null
    private var userListenerRegistration: ListenerRegistration? = null
    private var familyListenerRegistration: ListenerRegistration? = null

    init {
        // Observa o documento do utilizador para detetar mudanças na família
        val userId = auth.currentUser?.uid
        if (userId != null) {
            userListenerRegistration = db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                val newFamilyId = user?.familyId

                // Só recarrega os dados se o familyId realmente mudou
                if (newFamilyId != familyId) {
                    familyId = newFamilyId
                    if (newFamilyId != null) {
                        loadFamilyData(newFamilyId)
                    } else {
                        familyListenerRegistration?.remove()
                        familyListenerRegistration = null
                        _family.value = null
                        _members.value = emptyList()
                        _pendingMembers.value = emptyList()
                    }
                }
            }
        }
    }

    /**
     * Atualiza o estado de administrador do utilizador atual.
     */
    fun setIsAdmin(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
    }

    /**
     * Observa em tempo real o documento da família no Firestore.
     * Sempre que os dados mudam, recarrega os membros e pedidos pendentes.
     */
    private fun loadFamilyData(familyId: String) {
        familyListenerRegistration?.remove()
        familyListenerRegistration = db.collection("families").document(familyId)
            .addSnapshotListener { snapshot, _ ->
                val fam = snapshot?.toObject(Family::class.java)
                _family.value = fam
                fam?.let {
                    loadMembersWithRoles(it, AdapterMode.MEMBERS)
                    loadMembersWithRoles(it, AdapterMode.PENDING)
                }
            }
    }

    /**
     * Carrega os utilizadores correspondentes à lista de IDs da família
     * e mapeia cada um com o seu cargo. Atualiza o fluxo correto
     * consoante o modo indicado (membros ativos ou pedidos pendentes).
     */
    private fun loadMembersWithRoles(family: Family, mode: AdapterMode) {
        val idList = if (mode == AdapterMode.MEMBERS) family.members else family.pendingMembers

        // Se a lista estiver vazia, limpa o fluxo correspondente
        if (idList.isEmpty()) {
            if (mode == AdapterMode.MEMBERS) _members.value = emptyList()
            else _pendingMembers.value = emptyList()
            return
        }

        // Vai buscar cada perfil individualmente: as regras não permitem listar utilizadores.
        // Um perfil inacessível é ignorado, para não esconder os restantes.
        viewModelScope.launch {
            val users = coroutineScope {
                idList.map { uid ->
                    async {
                        try {
                            db.collection("users").document(uid).get().await().toObject(User::class.java)
                        } catch (e: FirebaseFirestoreException) {
                            Log.w(TAG, "Perfil inacessível", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val familyMembers = users.map { user ->
                val role = family.roles[user.uid] ?: "Membro"
                FamilyMember(user, role)
            }
            if (mode == AdapterMode.MEMBERS) _members.value = familyMembers
            else _pendingMembers.value = familyMembers
        }
    }

    /**
     * Gera um novo código de convite, válido por 24 horas, e invalida o anterior.
     * Invoca o callback com o código gerado após a operação ser concluída.
     */
    fun generateInviteCode(onCodeGenerated: (String) -> Unit, onError: () -> Unit) {
        val fId = familyId ?: return
        viewModelScope.launch {
            try {
                onCodeGenerated(familyRepository.createInvite(fId, _family.value?.inviteCode))
            } catch (e: FirebaseFirestoreException) {
                Log.w(TAG, "Falha ao gerar o convite", e)
                onError()
            }
        }
    }

    /**
     * Envia um pedido para entrar na família correspondente ao código de convite.
     * Valida o código e verifica se o utilizador já pertence à família.
     */
    fun requestToJoinFamily(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Utilizador não autenticado.")

        viewModelScope.launch {
            try {
                val targetFamilyId = familyRepository.findFamilyIdByInvite(inviteCode)
                when (targetFamilyId) {
                    null -> onError("Código de convite inválido ou expirado. Pede um novo ao administrador.")
                    familyId -> onError("Você já pertence a esta família.")
                    else -> {
                        familyRepository.requestToJoin(targetFamilyId, userId)
                        onSuccess()
                    }
                }
            } catch (e: FirebaseFirestoreException) {
                onError("Erro ao enviar pedido: ${e.message}")
            }
        }
    }

    /**
     * Aprova o pedido de adesão de um utilizador à família.
     * Usa uma transação para mover o utilizador dos pendentes para os membros ativos.
     */
    fun approveJoinRequest(userToJoin: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        val userRef = db.collection("users").document(userToJoin.uid)
        val familyRef = db.collection("families").document(currentFamId)

        db.runTransaction { transaction ->
            // Remove o utilizador dos pendentes, adiciona aos membros e define o cargo
            transaction.update(familyRef, "pendingMembers", FieldValue.arrayRemove(userToJoin.uid))
            transaction.update(familyRef, "members", FieldValue.arrayUnion(userToJoin.uid))
            transaction.update(familyRef, "roles.${userToJoin.uid}", "Membro")
            transaction.update(userRef, "familyId", currentFamId, "pendingFamilyId", FieldValue.delete())
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Falha na aprovação: ${e.message}") }
    }

    /**
     * Rejeita o pedido de adesão de um utilizador, removendo-o da lista de pendentes.
     */
    fun rejectJoinRequest(userToReject: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        val batch = db.batch()
        batch.update(
            db.collection("families").document(currentFamId),
            "pendingMembers", FieldValue.arrayRemove(userToReject.uid)
        )
        // Sem o pedido, o administrador deixa de poder ver o perfil desta pessoa
        batch.update(
            db.collection("users").document(userToReject.uid),
            "pendingFamilyId", FieldValue.delete()
        )
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Falha ao rejeitar: ${e.message}") }
    }

    /**
     * Remove um membro da família usando um batch para garantir consistência.
     * Atualiza simultaneamente o documento da família e o do utilizador removido.
     */
    fun removeMember(member: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        val famRef = db.collection("families").document(currentFamId)
        val userRef = db.collection("users").document(member.uid)
        val batch = db.batch()

        batch.update(
            famRef,
            "members", FieldValue.arrayRemove(member.uid),
            "roles.${member.uid}", FieldValue.delete()
        )
        batch.update(userRef, "familyId", null)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Falha ao remover: ${e.message}") }
    }

    /**
     * Permite ao utilizador autenticado sair da família.
     * Se for o último membro, apaga a família e todo o seu conteúdo,
     * que de outra forma ficaria guardado sem ninguém lhe poder aceder.
     */
    fun leaveFamily(user: User, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        val isLastMember = _family.value?.members?.all { it == user.uid } == true
        if (!isLastMember) {
            removeMember(user, onSuccess = {}, onError = onError)
            return
        }

        viewModelScope.launch {
            try {
                AccountDeletionRepository(db).deleteFamily(currentFamId)
                db.collection("users").document(user.uid).update("familyId", null).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao apagar a família", e)
                onError("Falha ao sair da família: ${e.message}")
            }
        }
    }

    /**
     * Cria uma nova família com o nome indicado.
     * O utilizador que a cria é automaticamente definido como administrador.
     */
    fun createFamily(name: String) {
        val userId = auth.currentUser?.uid ?: return

        val newFamily = Family(
            name = name,
            members = listOf(userId),
            roles = mapOf(userId to "Admin")
        )

        // Cria o documento da família e atualiza o familyId no documento do utilizador
        db.collection("families").add(newFamily)
            .addOnSuccessListener { familyDoc ->
                db.collection("users").document(userId).update("familyId", familyDoc.id)
            }
    }

    /**
     * Atualiza o nome da família no Firestore.
     */
    fun updateFamilyName(newName: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId).update("name", newName)
        }
    }

    /**
     * Atualiza o cargo de um membro específico no mapa de cargos da família.
     */
    fun updateMemberRole(userId: String, newRole: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId).update("roles.$userId", newRole)
        }
    }

    override fun onCleared() {
        super.onCleared()
        userListenerRegistration?.remove()
        familyListenerRegistration?.remove()
    }

    private companion object {
        const val TAG = "FamilyViewModel"
    }
}
