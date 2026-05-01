package com.example.restock

import com.example.restock.ui.inventario.CategoryUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** HUGO MOREIRA - a22402246
 * Testes unitários para CategoryUtils.
 * Verifica que a normalização e localização de categorias funciona
 * corretamente em ambas as direções (PT→neutro, EN→neutro, neutro→index).
 */
class CategoryUtilsTest {

    @Test
    fun `toNeutralKey converte portugues para chave neutra`() {
        assertEquals("grocery",    CategoryUtils.toNeutralKey("Mercearia"))
        assertEquals("dairy",      CategoryUtils.toNeutralKey("Lacticínios"))
        assertEquals("fruit",      CategoryUtils.toNeutralKey("Frutas"))
        assertEquals("vegetables", CategoryUtils.toNeutralKey("Legumes"))
        assertEquals("beverages",  CategoryUtils.toNeutralKey("Bebidas"))
        assertEquals("cleaning",   CategoryUtils.toNeutralKey("Limpeza"))
        assertEquals("hygiene",    CategoryUtils.toNeutralKey("Higiene"))
        assertEquals("other",      CategoryUtils.toNeutralKey("Outro"))
    }

    @Test
    fun `toNeutralKey converte ingles para chave neutra`() {
        assertEquals("grocery",    CategoryUtils.toNeutralKey("Grocery"))
        assertEquals("dairy",      CategoryUtils.toNeutralKey("Dairy"))
        assertEquals("fruit",      CategoryUtils.toNeutralKey("Fruit"))
        assertEquals("vegetables", CategoryUtils.toNeutralKey("Vegetables"))
        assertEquals("beverages",  CategoryUtils.toNeutralKey("Beverages"))
        assertEquals("cleaning",   CategoryUtils.toNeutralKey("Cleaning"))
        assertEquals("hygiene",    CategoryUtils.toNeutralKey("Hygiene"))
        assertEquals("other",      CategoryUtils.toNeutralKey("Other"))
    }

    @Test
    fun `toNeutralKey e case insensitive`() {
        assertEquals("grocery", CategoryUtils.toNeutralKey("GROCERY"))
        assertEquals("dairy",   CategoryUtils.toNeutralKey("DAIRY"))
        assertEquals("other",   CategoryUtils.toNeutralKey("OTHER"))
    }

    @Test
    fun `toNeutralKey devolve lowercase original para categoria desconhecida`() {
        val unknown = "desconhecida"
        assertEquals(unknown, CategoryUtils.toNeutralKey(unknown))
    }

    @Test
    fun `toNeutralKey aceita chaves neutras ja normalizadas`() {
        assertEquals("grocery",    CategoryUtils.toNeutralKey("grocery"))
        assertEquals("dairy",      CategoryUtils.toNeutralKey("dairy"))
        assertEquals("vegetables", CategoryUtils.toNeutralKey("vegetables"))
    }
}
