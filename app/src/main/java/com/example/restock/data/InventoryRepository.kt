package com.example.restock.data

import com.example.restock.model.HistoryItem
import com.example.restock.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/** HUGO MOREIRA - a22402246
 * Repositório responsável por todas as operações do inventário no Firestore.
 * Os ViewModels delegam aqui os acessos à base de dados, mantendo-se focados
 * apenas na lógica de negócio e na gestão do estado da interface.
 */
class InventoryRepository {

    private val db = FirebaseFirestore.getInstance()

    fun observeProducts(
        familyId: String,
        limit: Long,
        onChange: (List<Product>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("products")
            .limit(limit)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                onChange(snapshots?.toObjects(Product::class.java) ?: emptyList())
            }

    fun observeHistory(
        familyId: String,
        onChange: (List<HistoryItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("history")
            .orderBy("dataConsumo", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                onChange(snapshots?.toObjects(HistoryItem::class.java) ?: emptyList())
            }

    fun addProduct(familyId: String, product: Product) =
        db.collection("families").document(familyId)
            .collection("products").document(product.id).set(product)

    fun updateProduct(familyId: String, product: Product) =
        db.collection("families").document(familyId)
            .collection("products").document(product.id).set(product)

    fun deleteProduct(familyId: String, productId: String) =
        db.collection("families").document(familyId)
            .collection("products").document(productId).delete()

    fun addToHistory(familyId: String, item: HistoryItem) =
        db.collection("families").document(familyId)
            .collection("history").document(item.id).set(item)

    fun getFamilyName(familyId: String) =
        db.collection("families").document(familyId).get()
}
