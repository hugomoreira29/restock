package com.example.restock.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Apaga os dados associados a uma conta, exceto o documento do utilizador e a própria
 * conta de autenticação, que o AccountFragment apaga no fim.
 * Qualquer falha é propagada, para a conta não ser eliminada com dados por apagar.
 */
class AccountDeletionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    suspend fun deleteUserContent(uid: String, familyId: String?) {
        removeFromPendingRequests(uid)
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

    /**
     * Retira o utilizador dos pedidos de adesão pendentes noutras famílias.
     * Não bloqueia a eliminação: sem o documento do utilizador, um UID pendente
     * já não corresponde a nenhum dado pessoal e deixa de aparecer ao administrador.
     */
    private suspend fun removeFromPendingRequests(uid: String) {
        try {
            val requests = db.collection("families")
                .whereArrayContains("pendingMembers", uid)
                .get()
                .await()
            requests.documents.forEach { family ->
                family.reference.update("pendingMembers", FieldValue.arrayRemove(uid)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível limpar os pedidos de adesão pendentes", e)
        }
    }

    private suspend fun deleteFamily(familyId: String) {
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
        const val TAG = "AccountDeletion"
        const val MAX_BATCH_SIZE = 400
        val FAMILY_SUBCOLLECTIONS = listOf("products", "shopping_list", "history", "budgetHistory")
    }
}
