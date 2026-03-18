package com.example.restock.model

// Permite ler automaticamente o ID do documento Firestore e mapeá-lo para o campo id
import com.google.firebase.firestore.DocumentId

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa uma família na aplicação.
 * Contém informações como membros, cargos, orçamento mensal
 * e código de convite para partilha do inventário e lista de compras.
 */
data class Family(
    @DocumentId
    val id: String = "",    // Identificador único do documento no Firestore
    val name: String = "",  // Nome da família
    val members: List<String> = emptyList(),    // Lista dos IDs (UIDs) dos utilizadores que pertencem a esta família.
    val pendingMembers: List<String> = emptyList(), // Lista dos UIDs dos utilizadores à espera de aprovação
    val roles: Map<String, String> = emptyMap(),    // Mapa de cargos: userId -> "Admin", "Editor" ou "Leitor"
    val monthlyBudget: Double = 0.0,        // Orçamento mensal definido para a família.
    val inviteCode: String? = null  // Código de convite único para entrar na família
)
