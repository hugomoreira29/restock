package com.example.restock_pg_dispositivo_moveis.model

// HUGO MOREIRA - a22402246

data class ShoppingItem(
    val id: String = "",
    val nomeProduto: String = "",
    val quantidadeNecessaria: Double = 0.0,
    val comprado: Boolean = false,
    val familiaId: String = ""
)
