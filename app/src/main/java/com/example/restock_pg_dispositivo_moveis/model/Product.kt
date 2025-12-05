package com.example.restock_pg_dispositivo_moveis.model

data class Product(
    val id: String = "",
    val nome: String = "",
    val categoria: String = "",
    val quantidade: Double = 0.0,
    val unidade: String = "un",   // exemplo: kg, L, un
    val preco: Double = 0.0,
    val validade: Long? = null,   // timestamp
    val codigoBarras: String? = null,
    val imagemUrl: String? = null,
    val familiaId: String = ""
)
