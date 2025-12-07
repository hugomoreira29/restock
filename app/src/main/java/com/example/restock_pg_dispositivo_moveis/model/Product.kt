package com.example.restock_pg_dispositivo_moveis.model

import com.google.firebase.firestore.DocumentId

data class Product(
    @DocumentId
    val id: String = "",
    val nome: String = "",
    val categoria: String = "",
    val quantidade: Double = 0.0,
    val unidade: String = "un",
    val preco: Double = 0.0,
    val validade: Long? = null,
    val codigoBarras: String? = null,
    val imagemUrl: String? = null,
    val familiaId: String? = null // ID da família a que o produto pertence
)
