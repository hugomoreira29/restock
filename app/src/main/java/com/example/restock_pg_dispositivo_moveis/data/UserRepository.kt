package com.example.restock_pg_dispositivo_moveis.data

import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            db.collection("users").document(firebaseUser.uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
