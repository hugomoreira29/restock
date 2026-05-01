package com.example.restock.model

/** Representa o estado do orçamento de um determinado mês da família. */
data class BudgetSnapshot(
    val year: Int = 0,
    val month: Int = 0,              // 1-indexed: 1 = Janeiro, 12 = Dezembro
    val budget: Double = 0.0,
    val spent: Double = 0.0,
    val categories: Map<String, Double> = emptyMap()
)
