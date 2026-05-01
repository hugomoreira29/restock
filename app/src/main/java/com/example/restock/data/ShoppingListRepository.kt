package com.example.restock.data

import com.example.restock.model.ShoppingListItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** HUGO MOREIRA - a22402246
 * Repositório responsável por todas as operações da lista de compras no Firestore.
 */
class ShoppingListRepository {

    private val db = FirebaseFirestore.getInstance()

    fun observeItems(
        familyId: String,
        onChange: (List<ShoppingListItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("shopping_list")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                onChange(snapshots?.toObjects(ShoppingListItem::class.java) ?: emptyList())
            }

    fun addItem(familyId: String, item: ShoppingListItem) =
        db.collection("families").document(familyId)
            .collection("shopping_list").document(item.id).set(item)

    fun updateItem(familyId: String, item: ShoppingListItem) =
        db.collection("families").document(familyId)
            .collection("shopping_list").document(item.id).set(item)

    fun deleteItem(familyId: String, itemId: String) =
        db.collection("families").document(familyId)
            .collection("shopping_list").document(itemId).delete()
}
