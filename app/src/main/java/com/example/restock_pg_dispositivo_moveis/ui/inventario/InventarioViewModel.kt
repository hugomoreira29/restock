package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InventarioViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct

    init {
        fetchProducts()
    }

    private fun fetchProducts() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val productList = snapshots?.map { document ->
                    document.toObject(Product::class.java)
                } ?: emptyList()
                _produtos.value = productList
            }
    }

    fun loadProduct(productId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("products").document(productId)
            .get()
            .addOnSuccessListener { document ->
                _selectedProduct.value = document.toObject(Product::class.java)
            }
            .addOnFailureListener { 
                _selectedProduct.value = null
            }
    }
    
    fun clearSelectedProduct(){
        _selectedProduct.value = null
    }

    fun addProduct(product: Product) {
        val userId = auth.currentUser?.uid ?: return
        val newProduct = product.copy(familiaId = userId)
        db.collection("users").document(userId).collection("products")
            .document(newProduct.id)
            .set(newProduct)
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product added") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error adding document", e) }
    }

    fun updateProduct(product: Product) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("products")
            .document(product.id)
            .set(product)
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product updated") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error updating document", e) }
    }

    fun deleteProduct(productId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("products")
            .document(productId)
            .delete()
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product deleted") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error deleting document", e) }
    }
}
