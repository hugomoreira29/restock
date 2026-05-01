package com.example.restock.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/** HUGO MOREIRA - a22402246
 * Repositório responsável por todas as operações de família no Firestore.
 */
class FamilyRepository {

    private val db = FirebaseFirestore.getInstance()

    fun getFamilyByCode(inviteCode: String) =
        db.collection("families").whereEqualTo("inviteCode", inviteCode).get()

    fun getUsersByIds(ids: List<String>) =
        db.collection("users").whereIn("uid", ids).get()

    fun addToPendingMembers(familyId: String, userId: String) =
        db.collection("families").document(familyId)
            .update("pendingMembers", FieldValue.arrayUnion(userId))

    fun updateFamilyName(familyId: String, name: String) =
        db.collection("families").document(familyId).update("name", name)

    fun updateMemberRole(familyId: String, userId: String, role: String) =
        db.collection("families").document(familyId).update("roles.$userId", role)

    fun updateInviteCode(familyId: String, code: String, expiry: Long) =
        db.collection("families").document(familyId)
            .update("inviteCode", code, "inviteCodeExpiry", expiry)

    fun createFamily(family: com.example.restock.model.Family) =
        db.collection("families").add(family)

    fun updateUserFamilyId(userId: String, familyId: String?) =
        db.collection("users").document(userId).update("familyId", familyId)

    fun approveJoinRequest(
        familyId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val familyRef = db.collection("families").document(familyId)
        val userRef = db.collection("users").document(userId)
        db.runTransaction { transaction ->
            transaction.update(familyRef, "pendingMembers", FieldValue.arrayRemove(userId))
            transaction.update(familyRef, "members", FieldValue.arrayUnion(userId))
            transaction.update(familyRef, "roles.$userId", "Membro")
            transaction.update(userRef, "familyId", familyId)
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Falha na aprovação: ${e.message}") }
    }

    fun rejectJoinRequest(familyId: String, userId: String) =
        db.collection("families").document(familyId)
            .update("pendingMembers", FieldValue.arrayRemove(userId))

    fun removeMember(familyId: String, userId: String) =
        db.batch().also { batch ->
            val famRef = db.collection("families").document(familyId)
            val userRef = db.collection("users").document(userId)
            batch.update(famRef, "members", FieldValue.arrayRemove(userId), "roles.$userId", FieldValue.delete())
            batch.update(userRef, "familyId", null)
        }.commit()
}
