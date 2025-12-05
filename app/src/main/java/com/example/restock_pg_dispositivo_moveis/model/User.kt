package com.example.restock_pg_dispositivo_moveis.model

data class User(
    val uid: String = "",
    val nome: String = "",
    val email: String = "",
    val fotoPerfilUrl: String? = null,
    val familiaId: String? = null
)
