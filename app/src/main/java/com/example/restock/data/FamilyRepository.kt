package com.example.restock.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** HUGO MOREIRA - a22402246
 * Repositório responsável pelos convites e pelos pedidos de adesão a uma família.
 * Cada convite é um documento de "invites" cujo ID é o próprio código, para que só
 * o consiga abrir quem já sabe o código. Entrar numa família exige sempre a aprovação
 * de um administrador.
 */
class FamilyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun familyRef(familyId: String) = db.collection("families").document(familyId)
    private fun userRef(userId: String) = db.collection("users").document(userId)
    private fun inviteRef(code: String) = db.collection("invites").document(code)

    /** Devolve a família do convite, ou null se o código for inválido ou tiver expirado. */
    suspend fun findFamilyIdByInvite(rawCode: String): String? {
        val code = InviteCodes.normalize(rawCode) ?: return null
        val invite = inviteRef(code).get().await()
        val expiresAt = invite.getLong("expiresAt") ?: return null
        if (InviteCodes.isExpired(expiresAt, System.currentTimeMillis())) return null
        return invite.getString("familyId")
    }

    /** Para os registos, em que o documento do utilizador é criado no mesmo lote. */
    fun addJoinRequest(batch: WriteBatch, familyId: String, userId: String) {
        batch.update(familyRef(familyId), "pendingMembers", FieldValue.arrayUnion(userId))
    }

    /** Envia um pedido de adesão, substituindo um pedido anterior noutra família. */
    suspend fun requestToJoin(familyId: String, userId: String) {
        val previousFamilyId = userRef(userId).get().await().getString("pendingFamilyId")
        if (previousFamilyId != null && previousFamilyId != familyId) {
            cancelJoinRequest(previousFamilyId, userId)
        }

        val batch = db.batch()
        addJoinRequest(batch, familyId, userId)
        batch.update(userRef(userId), "pendingFamilyId", familyId)
        batch.commit().await()
    }

    /** Nunca falha: a família do pedido pode já ter sido apagada. */
    suspend fun cancelJoinRequest(familyId: String, userId: String) {
        try {
            familyRef(familyId)
                .update("pendingMembers", FieldValue.arrayRemove(userId))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível cancelar o pedido de adesão", e)
        }
    }

    /** Gera um novo convite e invalida o anterior. Devolve o código criado. */
    suspend fun createInvite(familyId: String, previousCode: String?): String {
        var lastError: FirebaseFirestoreException? = null
        repeat(MAX_CODE_ATTEMPTS) {
            val code = InviteCodes.generate()
            try {
                commitInvite(familyId, previousCode, code)
                return code
            } catch (e: FirebaseFirestoreException) {
                // Um código já usado por outra família é recusado pelas regras
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                lastError = e
            }
        }
        throw checkNotNull(lastError)
    }

    private suspend fun commitInvite(familyId: String, previousCode: String?, code: String) {
        val expiresAt = System.currentTimeMillis() + InviteCodes.VALID_FOR_MS
        val batch = db.batch()
        if (previousCode != null) deleteInviteIfOwned(batch, previousCode, familyId)
        batch.set(inviteRef(code), mapOf("familyId" to familyId, "expiresAt" to expiresAt))
        batch.update(familyRef(familyId), "inviteCode", code, "inviteCodeExpiry", expiresAt)
        batch.commit().await()
    }

    /** As regras recusam apagar um convite inexistente, por isso confirma-se primeiro. */
    suspend fun deleteInviteIfOwned(batch: WriteBatch, code: String, familyId: String) {
        val invite = inviteRef(code).get().await()
        if (invite.getString("familyId") == familyId) batch.delete(invite.reference)
    }

    private companion object {
        const val TAG = "FamilyRepository"
        const val MAX_CODE_ATTEMPTS = 3
    }
}
