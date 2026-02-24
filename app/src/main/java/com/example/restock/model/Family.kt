package com.example.restock.model

// HUGO MOREIRA - a224022- HUGO MOREIRA - a22402246

import com.google.firebase.firestore.DocumentId

/**
 * Data class que representa uma Família.
 * Cada documento nesta coleção conterá um inventário e uma lista de compras partilhados.
 */
data class Family(
    @DocumentId
    val id: String = "",
    val name: String = "",
    // Lista dos IDs (UIDs) dos utilizadores que pertencem a esta família.
    val members: List<String> = emptyList(),
    // Lista dos IDs (UIDs) dos utilizadores que pediram para entrar e aguardam aprovação.
    val pendingMembers: List<String> = emptyList(),
    // Mapa de cargos: userId -> role ("Admin", "Editor", "Leitor")
    val roles: Map<String, String> = emptyMap(),
    // Orçamento mensal definido para a família.
    val monthlyBudget: Double = 0.0, 
    // Código de convite único para esta família.
    val inviteCode: String? = null 
)
