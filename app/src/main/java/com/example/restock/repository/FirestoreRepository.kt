package com.example.restock.repository

// HUGO MOREIRA - a22402246

import com.example.restock.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    private val productsCollection = db.collection("products")

    /**
     * Guarda um produto no Firestore
     * Se id for vazio → cria automaticamente
     */
    suspend fun saveProduct(product: Product): Boolean {
        return try {
            val docRef = if (product.id.isEmpty()) {
                productsCollection.document() // novo id
            } else {
                productsCollection.document(product.id) // usa o id existente
            }

            val newProduct = product.copy(id = docRef.id)

            docRef.set(newProduct).await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtém lista de produtos pertencentes a uma familiaId
     */
    suspend fun getProductsByFamily(familiaId: String): List<Product> {
        return try {
            val snapshot: QuerySnapshot = productsCollection
                .whereEqualTo("familiaId", familiaId)
                .get()
                .await()

            snapshot.toObjects(Product::class.java)

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
