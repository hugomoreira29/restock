package com.example.restock.ui.family

// AndroidX - ViewModel para gerir os dados do ecrã de família
import androidx.lifecycle.ViewModel

// Classes internas da aplicação - repositório e modelos
import com.example.restock.data.UserRepository
import com.example.restock.model.Family
import com.example.restock.model.User

// Firebase - autenticação, base de dados e operações em array
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

        // Vai buscar os utilizadores pelos seus UIDs e associa o cargo de cada um
        db.collection("users").whereIn("uid", idList).get().addOnSuccessListener { userSnapshots ->
            val users = userSnapshots.toObjects(User::class.java)
            val familyMembers = users.map { user ->
                val role = family.roles[user.uid] ?: "Membro"
                FamilyMember(user, role)
            }
            if (mode == AdapterMode.MEMBERS) _members.value = familyMembers
            else _pendingMembers.value = familyMembers
        }
    }

    /**
     * Gera um código de convite aleatório de 6 caracteres e guarda-o no Firestore.
     * Invoca o callback com o código gerado após a operação ser concluída.
     */
    fun generateInviteCode(onCodeGenerated: (String) -> Unit) {
        familyId?.let { fId ->
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val newCode = (1..6).map { allowedChars.random() }.joinToString("")
            val expiry = System.currentTimeMillis() + 24 * 60 * 60 * 1000L // 24 horas
            db.collection("families").document(fId)
                .update("inviteCode", newCode, "inviteCodeExpiry", expiry)
                .addOnSuccessListener { onCodeGenerated(newCode) }
        }
    }

    /**
     * Envia um pedido para entrar na família correspondente ao código de convite.
     * Valida o código e verifica se o utilizador já pertence à família.
     */
    fun requestToJoinFamily(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Utilizador não autenticado.")

        db.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener onError("Código de convite inválido.")

                val familyDoc = documents.first()

                // Verifica se o código expirou (24h)
                val expiry = familyDoc.getLong("inviteCodeExpiry")
                if (expiry != null && System.currentTimeMillis() > expiry) {
                    return@addOnSuccessListener onError("O código de convite expirou. Pede um novo ao administrador.")
                }

                // Impede o utilizador de enviar um pedido para a família a que já pertence
                if (familyDoc.id == familyId) return@addOnSuccessListener onError("Você já pertence ou pediu para entrar nesta família.")

                // Adiciona o utilizador à lista de pedidos pendentes da família
                familyDoc.reference.update("pendingMembers", FieldValue.arrayUnion(userId))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError("Erro ao enviar pedido: ${e.message}") }
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
            transaction.update(userRef, "familyId", currentFamId)
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Falha na aprovação: ${e.message}") }
    }

    /**
     * Rejeita o pedido de adesão de um utilizador, removendo-o da lista de pendentes.
     */
    fun rejectJoinRequest(userToReject: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        familyId?.let {
            db.collection("families").document(it)
                .update("pendingMembers", FieldValue.arrayRemove(userToReject.uid))
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError("Falha ao rejeitar: ${e.message}") }
        } ?: onError("Família não encontrada.")
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
     * Reutiliza a lógica de remoção de membro.
     */
    fun leaveFamily(user: User) {
        removeMember(user, onSuccess = {}, onError = {})
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
}
