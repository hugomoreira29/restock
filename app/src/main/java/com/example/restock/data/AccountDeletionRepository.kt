package com.example.restock.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Apaga os dados associados a uma conta, exceto o documento do utilizador e a própria
 * conta de autenticação, que o AccountFragment apaga no fim.
 * Qualquer falha é propagada, para a conta não ser eliminada com dados por apagar.
 */
class AccountDeletionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val familyRepository: FamilyRepository = FamilyRepository(db)
) {

    suspend fun deleteUserContent(uid: String, familyId: String?) {
        val user = db.collection("users").document(uid).get().await()
        user.getString("pendingFamilyId")?.let { familyRepository.cancelJoinRequest(it, uid) }

        StoredImages.delete(storage.reference.child("profile_images/$uid"))

        if (familyId == null) return

        val familyRef = db.collection("families").document(familyId)
        val family = familyRef.get().await()
        if (!family.exists()) return

        val members = family.get("members") as? List<*> ?: emptyList<Any>()
        if (members.all { it == uid }) {
            deleteFamily(familyId)
        } else {
            leaveFamily(familyId, uid)
        }
    }

    /** Apaga a família e todo o seu conteúdo. Só o administrador ou o último membro o podem fazer. */
    suspend fun deleteFamily(familyId: String) {
        val familyRef = db.collection("families").document(familyId)

        // As imagens primeiro: os URLs só existem nos documentos dos produtos.
        val products = familyRef.collection("products").get().await()
        products.documents.forEach { product ->
            StoredImages.deleteByUrl(product.getString("imagemUrl"))
        }

        FAMILY_SUBCOLLECTIONS.forEach { name ->
            val documents = familyRef.collection(name).get().await().documents
            documents.chunked(MAX_BATCH_SIZE).forEach { chunk ->
                db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        }

        // O convite tem de sair antes da família, porque as regras dependem dela.
        val inviteCode = familyRef.get().await().getString("inviteCode")
        val batch = db.batch()
        if (inviteCode != null) familyRepository.deleteInviteIfOwned(batch, inviteCode, familyId)
        batch.commit().await()

        familyRef.delete().await()
    }

    private suspend fun leaveFamily(familyId: String, uid: String) {
        db.collection("families").document(familyId)
            .update(
                "members", FieldValue.arrayRemove(uid),
                "pendingMembers", FieldValue.arrayRemove(uid),
                "roles.$uid", FieldValue.delete()
            )
            .await()
    }

    private companion object {
        const val MAX_BATCH_SIZE = 400
        val FAMILY_SUBCOLLECTIONS = listOf("products", "shopping_list", "history", "budgetHistory")
    }
}
