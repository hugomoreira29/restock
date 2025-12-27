package com.example.restock_pg_dispositivo_moveis.model

import com.google.firebase.firestore.DocumentId

/**
 * Data class que representa um item na Lista de Compras.
 * Esta é a estrutura de dados guardada na sub-coleção "shopping_list" no Firestore.
 */
data class ShoppingListItem(
    // @DocumentId diz ao Firestore para mapear automaticamente o ID do documento para este campo.
    @DocumentId
    val id: String = "", // ID único do item na lista.
    val name: String = "", // Nome do item (ex: "Leite").
    val quantity: String = "", // Quantidade desejada (ex: "6 unidades", "1kg").
    val isChecked: Boolean = false // Estado que indica se o item já foi "comprado" (marcado na lista).
)
