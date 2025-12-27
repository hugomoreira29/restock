package com.example.restock_pg_dispositivo_moveis.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock_pg_dispositivo_moveis.data.UserRepository
import com.example.restock_pg_dispositivo_moveis.model.Family
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FamilyViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()

    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family

    private val _members = MutableStateFlow<List<User>>(emptyList())
    val members: StateFlow<List<User>> = _members

    private var familyId: String? = null

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.familyId?.let {
                familyId = it
                loadFamilyData(it)
                loadMembers(it)
            }
        }
    }

    private fun loadFamilyData(familyId: String) {
        db.collection("families").document(familyId)
            .addSnapshotListener { snapshot, _ ->
                _family.value = snapshot?.toObject(Family::class.java)
            }
    }

    private fun loadMembers(familyId: String) {
        db.collection("families").document(familyId).get().addOnSuccessListener { document ->
            val memberIds = document.toObject(Family::class.java)?.members ?: return@addOnSuccessListener
            if (memberIds.isNotEmpty()) {
                db.collection("users").whereIn("uid", memberIds).get().addOnSuccessListener { userSnapshots ->
                    _members.value = userSnapshots.toObjects(User::class.java)
                }
            }
        }
    }

    fun generateInviteCode(onCodeGenerated: (String) -> Unit) {
        familyId?.let {
            // Gera um código simples de 6 caracteres
            val newCode = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString("")
            db.collection("families").document(it)
                .update("inviteCode", newCode)
                .addOnSuccessListener { 
                    onCodeGenerated(newCode)
                }
        }
    }
}
