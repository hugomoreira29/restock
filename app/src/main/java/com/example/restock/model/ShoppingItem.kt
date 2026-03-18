package com.example.restock.model

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa um item na lista de compras.
 * Guarda informações sobre o produto necessário, a quantidade,
 * o estado de compra e a família à qual o item pertence.
 */

data class ShoppingItem(
    val id: String = "",    // Identificador único do item
    val nomeProduto: String = "",   // Nome do produto a comprar
    val quantidadeNecessaria: Double = 0.0, // Quantidade necessária do produto
    val comprado: Boolean = false,  // Indica se o item já foi comprado (true) ou não (false)
    val familiaId: String = ""  // Identificador da família à qual a lista de compras pertence
)
