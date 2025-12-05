package com.example.restock_pg_dispositivo_moveis.model

data class Family(
    val id: String = "",
    val nome: String = "",
    val membros: List<String> = emptyList() // lista de UIDs dos users
)
