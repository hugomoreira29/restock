package com.example.restock.ui.inventario

import android.content.Context
import com.example.restock.R

object CategoryUtils {
    private val categoryIndex = mapOf(
        "mercearia" to 0, "grocery" to 0, "groceries" to 0,
        "lacticínios" to 1, "lacticinios" to 1, "dairy" to 1,
        "frutas" to 2, "fruits" to 2, "fruit" to 2,
        "legumes" to 3, "vegetables" to 3, "vegetable" to 3, "vegetal" to 3,
        "bebidas" to 4, "beverages" to 4, "beverage" to 4,
        "limpeza" to 5, "cleaning" to 5,
        "higiene" to 6, "hygiene" to 6,
        "outro" to 7, "other" to 7
    )

    private val neutralKeys = listOf(
        "grocery", "dairy", "fruit", "vegetables",
        "beverages", "cleaning", "hygiene", "other"
    )

    fun localize(category: String, context: Context): String {
        val index = categoryIndex[category.lowercase().trim()] ?: return category
        return context.resources.getStringArray(R.array.product_categories).getOrNull(index) ?: category
    }

    // Devolve a chave neutra (inglês lowercase) para guardar no Firestore,
    // independentemente do idioma do utilizador que selecionou a categoria.
    fun toNeutralKey(category: String): String {
        val index = categoryIndex[category.lowercase().trim()] ?: return category.lowercase().trim()
        return neutralKeys[index]
    }
}
