package com.example.restock.data

// Modelo do utilizador para mapeamento dos dados do Firestore
import com.example.restock.model.User

// Firebase - autenticação e base de dados
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Coroutines - para executar operações assíncronas sem bloquear a aplicação
import kotlinx.coroutines.tasks.await

/** HUGO MOREIRA - a22402246
 * GamificationRepository.kt
 * Responsável por gerir a gamificação dos utilizadores.
 * Trata da atribuição de pontos e do cálculo de níveis,
 * guardando tudo na base de dados Firestore em tempo real.
 */
class GamificationRepository {

    // Instância da base de dados Firestore
    private val db = FirebaseFirestore.getInstance()

    // Instância de autenticação para obter o utilizador atual
    private val auth = FirebaseAuth.getInstance()

    /**
     * Adiciona pontos ao utilizador autenticado e atualiza o nível se necessário.
     * Devolve o novo total de pontos, ou null se houver algum erro.
     */
    suspend fun addPoints(points: Int): Int? {

        // Verifica se há um utilizador autenticado; se não houver, cancela a operação
        val userId = auth.currentUser?.uid ?: return null

        // Referência ao documento do utilizador na coleção "users"
        val userRef = db.collection("users").document(userId)

        return try {
            // Executa uma transação para garantir que os dados são atualizados de forma segura
            db.runTransaction { transaction ->

                // Lê os dados atuais do utilizador
                val snapshot = transaction.get(userRef)
                val user = snapshot.toObject(User::class.java) ?: return@runTransaction null

                // Calcula os novos pontos e o novo nível com base nesses pontos
                val newPoints = user.points + points
                val newLevel = calculateLevel(newPoints)

                // Atualiza os pontos do utilizador na base de dados
                transaction.update(userRef, "points", newPoints)

                // Só atualiza o nível se o utilizador tiver subido de nível
                if (newLevel > user.level) {
                    transaction.update(userRef, "level", newLevel)
                }

                // Devolve o novo total de pontos
                newPoints

            }.await()

        } catch (e: Exception) {
            // Em caso de erro, imprime o mesmo e devolve null
            e.printStackTrace()
            null
        }
    }

    /**
     * Calcula o nível do utilizador com base nos pontos totais.
     * A cada 100 pontos o utilizador sobe um nível, começando no nível 1.
     */
    private fun calculateLevel(points: Int): Int {
        return (points / 100) + 1
    }
}
