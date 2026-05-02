package com.example.restock

import com.example.restock.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** HUGO MOREIRA - a22402246
 * Testes unitários para a lógica de cálculo do orçamento mensal.
 * Verifica o filtro por mês, o total gasto e os gastos por categoria.
 */
class BudgetCalculationTest {

    private fun makeProduct(
        nome: String,
        preco: Double,
        quantidade: Double,
        categoria: String,
        monthsAgo: Int = 0
    ): Product {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
        return Product(
            id = nome,
            nome = nome,
            preco = preco,
            quantidade = quantidade,
            categoria = categoria,
            createdAt = cal.time
        )
    }

    @Test
    fun `total gasto no mes atual esta correto`() {
        val products = listOf(
            makeProduct("Leite",  1.5, 2.0, "dairy"),   // 3.00
            makeProduct("Pao",    0.8, 3.0, "grocery"),  // 2.40
            makeProduct("Arroz",  1.2, 1.0, "grocery", monthsAgo = 1),  // 1.20 (mes passado)
        ).let { all ->
            val cal = Calendar.getInstance()
            val currentMonth = cal.get(Calendar.MONTH)
            val currentYear  = cal.get(Calendar.YEAR)
            all.filter { p ->
                p.createdAt?.let {
                    val pc = Calendar.getInstance().apply { time = it }
                    pc.get(Calendar.MONTH) == currentMonth && pc.get(Calendar.YEAR) == currentYear
                } ?: false
            }
        }

        val total = products.sumOf { it.preco * it.quantidade }
        assertEquals(5.40, total, 0.001)
    }

    @Test
    fun `gastos por categoria estao corretos`() {
        val products = listOf(
            makeProduct("Leite",  1.5, 2.0, "dairy"),
            makeProduct("Queijo", 2.0, 1.0, "dairy"),
            makeProduct("Pao",    0.8, 3.0, "grocery"),
        )

        val categoryMap = products
            .groupBy { it.categoria }
            .mapValues { (_, items) -> items.sumOf { it.preco * it.quantidade } }

        assertEquals(5.0,  categoryMap["dairy"]   ?: 0.0, 0.001)
        assertEquals(2.4,  categoryMap["grocery"]  ?: 0.0, 0.001)
    }

    @Test
    fun `produtos de meses anteriores nao entram no calculo`() {
        val thisMonth = makeProduct("Leite", 1.5, 2.0, "dairy", monthsAgo = 0)
        val lastMonth = makeProduct("Arroz", 2.0, 1.0, "grocery", monthsAgo = 1)

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear  = cal.get(Calendar.YEAR)

        val filtered = listOf(thisMonth, lastMonth).filter { p ->
            p.createdAt?.let {
                val pc = Calendar.getInstance().apply { time = it }
                pc.get(Calendar.MONTH) == currentMonth && pc.get(Calendar.YEAR) == currentYear
            } ?: false
        }

        assertEquals(1, filtered.size)
        assertEquals("Leite", filtered.first().nome)
    }

    @Test
    fun `debounce snap shot key tem formato correto`() {
        val cal = Calendar.getInstance()
        val year  = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val key   = "$year-${String.format("%02d", month)}"

        // Verifica que o formato é YYYY-MM com zero à esquerda no mês
        assert(key.matches(Regex("\\d{4}-\\d{2}")))
    }
}
