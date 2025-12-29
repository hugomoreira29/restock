package com.example.restock_pg_dispositivo_moveis.model

// HUGO MOREIRA - a22402246

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class que representa um Produto no inventário.
 * Esta é a estrutura de dados que é guardada na sub-coleção "products" no Firestore.
 */
data class Product(
    // @DocumentId diz ao Firestore para mapear automaticamente o ID do documento para este campo.
    @DocumentId
    val id: String = "", // ID único do produto.
    val nome: String = "", // Nome do produto.
    val categoria: String = "", // Categoria a que o produto pertence.
    val quantidade: Double = 0.0, // Quantidade do produto.
    val unidade: String = "un", // Unidade de medida (ex: kg, L, un).
    val preco: Double = 0.0, // Preço do produto.
    val validade: Long? = null, // Data de validade, guardada como um timestamp (Long).
    val codigoBarras: String? = null, // Código de barras do produto.
    val imagemUrl: String? = null, // URL da imagem do produto guardada no Firebase Storage.
    val familiaId: String? = null, // ID da família a que o inventário pertence.

    // NOVO: Data de criação, preenchida automaticamente pelo servidor.
    @ServerTimestamp
    var createdAt: Date? = null
)
