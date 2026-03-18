package com.example.restock.model

// Firebase - para mapear o ID do documento e a data de criação automática do servidor
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

// Java - para representar a data de criação do produto
import java.util.Date

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa um produto no inventário.
 * Contém todas as informações do produto como nome, categoria,
 * quantidade, preço, validade e código de barras, sendo guardado
 * na sub-coleção "products" do Firestore.
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

    @ServerTimestamp
    var createdAt: Date? = null // Data de criação do produto, preenchida automaticamente pelo servidor
)
