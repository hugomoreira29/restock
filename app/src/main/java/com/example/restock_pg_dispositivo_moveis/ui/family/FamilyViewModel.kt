package com.example.restock_pg_dispositivo_moveis.ui.family

// HUGO MOREIRA - a22402246

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock_pg_dispositivo_moveis.data.UserRepository
import com.example.restock_pg_dispositivo_moveis.model.Family
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Classe auxiliar para a UI, combinando dados do utilizador com o seu cargo
data class FamilyMember(
    val user: User,
    val role: String
)

class FamilyViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family

    // Agora expomos FamilyMember em vez de apenas User
    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members

    private var familyId: String? = null

    init {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                val newFamilyId = user?.familyId
                
                if (newFamilyId != familyId) {
                    familyId = newFamilyId
                    if (newFamilyId != null) {
                        loadFamilyData(newFamilyId)
                    } else {
                        _family.value = null
                        _members.value = emptyList()
                    }
                }
            }
        }
    }

    private fun loadFamilyData(familyId: String) {
        db.collection("families").document(familyId)
            .addSnapshotListener { snapshot, _ ->
                val fam = snapshot?.toObject(Family::class.java)
                _family.value = fam
                
                // Recarregar membros com os cargos atualizados
                fam?.let { loadMembersWithRoles(it) }
            }
    }

    private fun loadMembersWithRoles(family: Family) {
        val memberIds = family.members
        if (memberIds.isNotEmpty()) {
            db.collection("users").whereIn("uid", memberIds).get().addOnSuccessListener { userSnapshots ->
                val users = userSnapshots.toObjects(User::class.java)
                
                // Mapear cada utilizador para um FamilyMember com o seu cargo
                val familyMembers = users.map { user ->
                    // Tenta obter o cargo do mapa. Se não existir, verifica se é o criador da família.
                    var role = family.roles[user.uid]
                    
                    if (role == null) {
                        // Lógica de Fallback (Segurança):
                        // Se for o primeiro membro da lista (índice 0), assumimos que é o Admin/Criador.
                        if (family.members.isNotEmpty() && family.members[0] == user.uid) {
                            role = "Admin"
                            // Opcional: Auto-corrigir na base de dados se quiser
                            // updateMemberRole(user.uid, "Admin") 
                        } else {
                            role = "Membro"
                        }
                    }
                    
                    FamilyMember(user, role ?: "Membro")
                }
                
                _members.value = familyMembers
            }
        } else {
             _members.value = emptyList()
        }
    }

    fun generateInviteCode(onCodeGenerated: (String) -> Unit) {
        familyId?.let {
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val newCode = (1..6).map { allowedChars.random() }.joinToString("")
            
            db.collection("families").document(it)
                .update("inviteCode", newCode)
                .addOnSuccessListener { 
                    onCodeGenerated(newCode)
                }
        }
    }

    fun joinFamily(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("Utilizador não autenticado.")
            return
        }

        db.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onError("Código de convite inválido ou expirado.")
                    return@addOnSuccessListener
                }

                val newFamilyDoc = documents.first()
                val newFamilyId = newFamilyDoc.id

                if (newFamilyId == familyId) {
                    onError("Você já pertence a esta família.")
                    return@addOnSuccessListener
                }

                if (familyId != null) {
                    db.collection("families").document(familyId!!)
                        .update("members", FieldValue.arrayRemove(userId))
                }

                val batch = db.batch()

                val userRef = db.collection("users").document(userId)
                batch.update(userRef, "familyId", newFamilyId)

                val familyRef = db.collection("families").document(newFamilyId)
                batch.update(familyRef, "members", FieldValue.arrayUnion(userId))
                // Define cargo padrão como "Membro" ao entrar
                batch.update(familyRef, "roles.$userId", "Membro")
                
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError("Erro ao entrar na família: ${e.message}") }
            }
            .addOnFailureListener { e ->
                onError("Erro ao procurar família: ${e.message}")
            }
    }
    
    fun removeMember(member: User) {
        val currentFamId = familyId ?: return
        
        val batch = db.batch()
        val famRef = db.collection("families").document(currentFamId)
        
        batch.update(famRef, "members", FieldValue.arrayRemove(member.uid))
        batch.update(famRef, "roles.${member.uid}", FieldValue.delete())
        
        val userRef = db.collection("users").document(member.uid)
        batch.update(userRef, "familyId", null)
        
        batch.commit()
    }

    fun updateFamilyName(newName: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId)
                .update("name", newName)
        }
    }

    fun updateMemberRole(userId: String, newRole: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId)
                .update("roles.$userId", newRole)
        }
    }
}
