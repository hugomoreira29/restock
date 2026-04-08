package com.example.restock

import com.example.restock.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Teste unitário para validar a integridade do modelo de dados do Produto.
 * Garante que os cálculos de quantidade e atribuições de campos estão corretos.
 */
class ProductModelTest {

    @Test
    fun `test product creation and field assignment`() {
        val product = Product(
            id = "test_id",
            nome = "Leite",
            categoria = "Laticínios",
            quantidade = 2.0,
            unidade = "L",
            preco = 0.99
        )

        assertEquals("test_id", product.id)
        assertEquals("Leite", product.nome)
        assertEquals(2.0, product.quantidade, 0.0)
        assertEquals("L", product.unidade)
    }

    @Test
    fun `test product default values`() {
        val product = Product()
        
        assertEquals("", product.nome)
        assertEquals(0.0, product.quantidade, 0.0)
        assertEquals("un", product.unidade)
    }
}
