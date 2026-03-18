package com.example.restock.model

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa um registo no histórico de consumo.
 * Guarda informações sobre o produto consumido, a quantidade,
 * a data de consumo e a família à qual o registo pertence.
*/

data class HistoryItem(
    val id: String = "",    // Identificador único do registo
    val produto: String = "",   // Nome do produto consumido
    val quantidade: Double = 0.0,   // Quantidade consumida do produto
    val dataConsumo: Long = 0L,   // Data do consumo em formato timestamp (milissegundos)
    val familiaId: String = ""  // Identificador da família à qual o registo pertence
)
