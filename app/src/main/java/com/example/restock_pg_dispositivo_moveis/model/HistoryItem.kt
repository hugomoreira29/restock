package com.example.restock_pg_dispositivo_moveis.model

data class HistoryItem(
    val id: String = "",
    val produto: String = "",
    val quantidade: Double = 0.0,
    val dataConsumo: Long = 0L,   // timestamp
    val familiaId: String = ""
)
