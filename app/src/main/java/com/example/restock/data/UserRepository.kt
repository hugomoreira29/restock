package com.example.restock.data

// Modelo do utilizador para mapeamento dos dados do Firestore
import com.example.restock.model.User

// Firebase - autenticação e base de dados
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Coroutines - para executar operações assíncronas sem bloquear a aplicação
import kotlinx.coroutines.tasks.await

/** HUGO MOREIRA - a22402246
 * UserRepository.kt
 * Responsável por gerir os dados do utilizador autenticado.
 * Obtém as informações do utilizador atual a partir da base
 * de dados Firestore, usando o Firebase Authentication.
 */
class UserRepository {

    // Instância da base de dados Firestore
    private val db = FirebaseFirestore.getInstance()

    // Instância de autenticação para obter o utilizador atual
    private val auth = FirebaseAuth.getInstance()

    /**
     * Obtém os dados completos do utilizador autenticado a partir do Firestore.
     * Devolve um objeto User com as informações do utilizador,
     * ou null caso não haja sessão iniciada ou ocorra algum erro.
     */
    suspend fun getCurrentUser(): User? {

        // Verifica se há um utilizador autenticado; se não houver, cancela a operação
        val firebaseUser = auth.currentUser ?: return null

        return try {
            // Vai buscar o documento do utilizador à coleção "users" usando o seu uid
            db.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()
                .toObject(User::class.java)

        } catch (e: Exception) {
            // Em caso de erro na leitura, devolve null
            null
        }
    }
}