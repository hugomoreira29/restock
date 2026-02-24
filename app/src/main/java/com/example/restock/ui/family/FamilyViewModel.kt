package com.example.restock.ui.family

import androidx.lifecycle.ViewModel
import com.example.restock.data.UserRepository
import com.example.restock.model.Family
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members

    private val _pendingMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val pendingMembers: StateFlow<List<FamilyMember>> = _pendingMembers
    
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

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
                        _pendingMembers.value = emptyList()
                    }
                }
            }
        }
    }
    
    fun setIsAdmin(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
    }

    private fun loadFamilyData(familyId: String) {
        db.collection("families").document(familyId).addSnapshotListener { snapshot, _ ->
            val fam = snapshot?.toObject(Family::class.java)
            _family.value = fam
            fam?.let {
                loadMembersWithRoles(it, AdapterMode.MEMBERS)
                loadMembersWithRoles(it, AdapterMode.PENDING)
            }
        }
    }

    private fun loadMembersWithRoles(family: Family, mode: AdapterMode) {
        val idList = if (mode == AdapterMode.MEMBERS) family.members else family.pendingMembers
        if (idList.isEmpty()) {
            if (mode == AdapterMode.MEMBERS) _members.value = emptyList()
            else _pendingMembers.value = emptyList()
            return
        }

        db.collection("users").whereIn("uid", idList).get().addOnSuccessListener { userSnapshots ->
            val users = userSnapshots.toObjects(User::class.java)
            val familyMembers = users.map { user ->
                val role = family.roles[user.uid] ?: "Membro"
                FamilyMember(user, role)
            }
            if (mode == AdapterMode.MEMBERS) {
                _members.value = familyMembers
            } else {
                _pendingMembers.value = familyMembers
            }
        }
    }

    fun generateInviteCode(onCodeGenerated: (String) -> Unit) {
        familyId?.let {
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val newCode = (1..6).map { allowedChars.random() }.joinToString("")
            db.collection("families").document(it).update("inviteCode", newCode)
                .addOnSuccessListener { onCodeGenerated(newCode) }
        }
    }

    fun requestToJoinFamily(inviteCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Utilizador não autenticado.")

        db.collection("families").whereEqualTo("inviteCode", inviteCode).get().addOnSuccessListener { documents ->
            if (documents.isEmpty) return@addOnSuccessListener onError("Código de convite inválido.")
            
            val familyDoc = documents.first()
            if (familyDoc.id == familyId) return@addOnSuccessListener onError("Você já pertence ou pediu para entrar nesta família.")

            familyDoc.reference.update("pendingMembers", FieldValue.arrayUnion(userId))
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError("Erro ao enviar pedido: ${e.message}") }
        }
    }

    fun approveJoinRequest(userToJoin: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        val userRef = db.collection("users").document(userToJoin.uid)
        val familyRef = db.collection("families").document(currentFamId)
        
        db.runTransaction {
            transaction ->
            transaction.update(familyRef, "pendingMembers", FieldValue.arrayRemove(userToJoin.uid))
            transaction.update(familyRef, "members", FieldValue.arrayUnion(userToJoin.uid))
            transaction.update(familyRef, "roles.${userToJoin.uid}", "Membro")
            transaction.update(userRef, "familyId", currentFamId)
            null
        }.addOnSuccessListener { onSuccess() }
         .addOnFailureListener { e -> onError("Falha na aprovação: ${e.message}") }
    }

    fun rejectJoinRequest(userToReject: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        familyId?.let {
            db.collection("families").document(it)
                .update("pendingMembers", FieldValue.arrayRemove(userToReject.uid))
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError("Falha ao rejeitar: ${e.message}") }
        } ?: onError("Família não encontrada.")
    }

    fun removeMember(member: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentFamId = familyId ?: return onError("Família não encontrada.")
        
        val batch = db.batch()
        val famRef = db.collection("families").document(currentFamId)
        
        batch.update(famRef, "members", FieldValue.arrayRemove(member.uid))
        batch.update(famRef, "roles.${member.uid}", FieldValue.delete())
        
        val userRef = db.collection("users").document(member.uid)
        batch.update(userRef, "familyId", null)
        
        batch.commit()
             .addOnSuccessListener { onSuccess() }
             .addOnFailureListener { e -> onError("Falha ao remover: ${e.message}") }
    }
    
    fun leaveFamily(user: User) {
        removeMember(user, onSuccess = {}, onError = {})
    }

    fun createFamily(name: String) {
        val userId = auth.currentUser?.uid ?: return
        
        val newFamily = Family(
            name = name,
            members = listOf(userId),
            roles = mapOf(userId to "Admin")
        )

        val userRef = db.collection("users").document(userId)
        
        db.collection("families").add(newFamily)
            .addOnSuccessListener { familyDoc ->
                userRef.update("familyId", familyDoc.id)
            }
    }

    fun updateFamilyName(newName: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId).update("name", newName)
        }
    }

    fun updateMemberRole(userId: String, newRole: String) {
        familyId?.let { fId ->
            db.collection("families").document(fId).update("roles.$userId", newRole)
        }
    }
}
