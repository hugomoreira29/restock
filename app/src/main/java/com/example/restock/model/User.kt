package com.example.restock.model

// Firebase - para preencher automaticamente a data de criação pelo servidor
import com.google.firebase.firestore.ServerTimestamp

// Java - para representar a data de criação do utilizador
import java.util.Date

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa um utilizador na aplicação.
 */
data class User(
    var uid: String = "",             // Identificador único do utilizador no Firebase Authentication
    var name: String = "",            // Nome do utilizador
    var email: String = "",           // Endereço de email do utilizador
    var photoUrl: String? = null,     // URL da fotografia de perfil do utilizador, pode ser nula
    var familyId: String? = null,     // Identificador da família a que o utilizador pertence, pode ser nulo
    var points: Int = 0,              // Pontos acumulados pelo utilizador no sistema de gamificação
    var level: Int = 1,               // Nível atual do utilizador, começa no nível 1

    @ServerTimestamp
    var createdAt: Date? = null       // Data de criação da conta, preenchida automaticamente pelo servidor
)
