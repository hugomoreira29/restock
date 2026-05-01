package com.example.restock.model

// Firebase - para mapear automaticamente o ID do documento para o campo id
import com.google.firebase.firestore.DocumentId

/** HUGO MOREIRA - a22402246
 * Modelo de dados que representa um item na lista de compras.
 * Guarda o nome, a quantidade e o estado de compra do item,
 * sendo guardado na sub-coleção "shopping_list" do Firestore.
 */
data class ShoppingListItem(
    // @DocumentId diz ao Firestore para mapear automaticamente o ID do documento para este campo.
    @DocumentId
    val id: String = "", // ID único do item na lista.
    val name: String = "", // Nome do item (ex: "Leite", "Pão")
    val quantity: String = "", // Quantidade desejada (ex: "6 unidades", "1 kg")
    val isChecked: Boolean = false, // Indica se o item já foi comprado (true) ou ainda está por comprar (false)
    val boughtBy: String? = null   // Nome do membro que marcou como comprado
)
