package com.example.restock_pg_dispositivo_moveis.model

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
    // Código de convite único para esta família.
    val inviteCode: String? = null 
)
