package com.example.restock_pg_dispositivo_moveis.ui.inventario

// HUGO MOREIRA - a22402246

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InventarioViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct

    // Novo StateFlow para o nome da família
    private val _familyName = MutableStateFlow<String>("")
    val familyName: StateFlow<String> = _familyName
    
    // Armazena o ID da família atual para operações CRUD
    private var currentFamilyId: String? = null
    
    // Registo do listener de produtos para podermos cancelá-lo quando a família muda
    private var productsListener: ListenerRegistration? = null

    init {
        // Inicia a observação do utilizador para detetar mudanças de família
        observeUserFamily()
    }

    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return

        // Ouve mudanças no documento do utilizador (ex: mudança de familyId)
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen user failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    val newFamilyId = user?.familyId

                    // Se o familyId mudou, atualiza a escuta de produtos e busca o nome da família
                    if (newFamilyId != currentFamilyId) {
                        currentFamilyId = newFamilyId
                        if (newFamilyId != null) {
                            fetchProducts(newFamilyId)
                            fetchFamilyName(newFamilyId)
                        } else {
                            // Se não tiver família, limpa a lista e o nome
                            _produtos.value = emptyList()
                            _familyName.value = ""
                            productsListener?.remove()
                        }
                    }
                }
            }
    }

    private fun fetchFamilyName(familyId: String) {
        db.collection("families").document(familyId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: ""
                    _familyName.value = name
                }
            }
    }

    private fun fetchProducts(familyId: String) {
        // Remove o listener anterior se existir
        productsListener?.remove()

        // Adiciona um novo listener para a coleção de produtos da nova família
        productsListener = db.collection("families").document(familyId).collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen products failed.", e)
                    return@addSnapshotListener
                }

                val productList = snapshots?.map { document ->
                    document.toObject(Product::class.java)
                } ?: emptyList()
                _produtos.value = productList
            }
    }

    fun loadProduct(productId: String) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("products").document(productId)
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
        val fId = currentFamilyId ?: return
        val newProduct = product.copy(familiaId = fId)
        
        db.collection("families").document(fId).collection("products")
            .document(newProduct.id)
            .set(newProduct)
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product added to family $fId") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error adding document", e) }
    }

    fun updateProduct(product: Product) {
        val fId = currentFamilyId ?: return
        
        db.collection("families").document(fId).collection("products")
            .document(product.id)
            .set(product)
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product updated in family $fId") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error updating document", e) }
    }

    fun deleteProduct(productId: String) {
        val fId = currentFamilyId ?: return
        
        db.collection("families").document(fId).collection("products")
            .document(productId)
            .delete()
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product deleted from family $fId") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error deleting document", e) }
    }
    
    override fun onCleared() {
        super.onCleared()
        productsListener?.remove()
    }
}
