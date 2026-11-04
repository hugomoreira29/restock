package com.example.restock.data

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await

/**
 * Remoção de imagens guardadas no Firebase Storage.
 * Os produtos também podem apontar para imagens externas (Open Food Facts),
 * que não são nossas e por isso nunca são apagadas.
 */
object StoredImages {

    private const val STORAGE_URL_PREFIX = "https://firebasestorage.googleapis.com/"

    fun isStoredInFirebase(url: String?): Boolean =
        url?.startsWith(STORAGE_URL_PREFIX) == true

    suspend fun deleteByUrl(url: String?) {
        if (!isStoredInFirebase(url)) return
        delete(FirebaseStorage.getInstance().getReferenceFromUrl(url!!))
    }

    /** Um ficheiro que já não existe conta como apagado; os restantes erros são propagados. */
    suspend fun delete(reference: StorageReference) {
        try {
            reference.delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }
}
