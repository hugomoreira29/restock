package com.example.restock

import com.example.restock.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Suite de Testes Unitários de Integridade de Modelos.
 * Valida se todos os modelos de dados da aplicação ReStock suportam
 * os campos necessários e mantêm a integridade dos tipos.
 */
class ModelIntegrityTest {

    @Test
    fun `test User model creation and fields`() {
        val user = User(
            uid = "user_123",
            name = "Hugo Moreira",
            email = "hugo@example.com",
            points = 150,
            level = 2,
            familyId = "fam_456"
        )
        assertEquals("user_123", user.uid)
        assertEquals("Hugo Moreira", user.name)
        assertEquals(150, user.points)
        assertEquals(2, user.level)
        assertEquals("fam_456", user.familyId)
    }

    @Test
    fun `test Family model permissions and budget`() {
        val family = Family(
            id = "fam_456",
            name = "Família Teste",
            members = listOf("user_1", "user_2"),
            roles = mapOf("user_1" to "Admin", "user_2" to "Editor"),
            monthlyBudget = 500.0,
            inviteCode = "ABCDEF"
        )
        assertEquals("Família Teste", family.name)
        assertEquals(2, family.members.size)
        assertEquals("Admin", family.roles["user_1"])
        assertEquals(500.0, family.monthlyBudget, 0.0)
        assertEquals("ABCDEF", family.inviteCode)
    }

    @Test
    fun `test Product validation logic`() {
        val product = Product(
            nome = "Arroz",
            quantidade = 5.0,
            unidade = "kg",
            preco = 1.20,
            validade = 1735689600000L
        )
        assertEquals("Arroz", product.nome)
        assertTrue("Quantidade deve ser positiva", product.quantidade >= 0)
        assertEquals(1.20, product.preco, 0.0)
        assertNotNull(product.validade)
    }

    @Test
    fun `test ShoppingListItem states`() {
        val item = ShoppingListItem(
            name = "Maçãs",
            quantity = "2 kg",
            isChecked = false
        )
        assertEquals("Maçãs", item.name)
        assertFalse("Item novo não deve estar marcado", item.isChecked)
        
        val itemChecked = item.copy(isChecked = true)
        assertTrue("Item deve poder ser marcado como comprado", itemChecked.isChecked)
    }

    @Test
    fun `test Category model`() {
        val category = Category(
            id = "cat_1",
            nome = "Laticínios"
        )
        assertEquals("Laticínios", category.nome)
    }
}
