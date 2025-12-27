package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock_pg_dispositivo_moveis.data.UserRepository
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InventarioViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()

    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct

    private var familyId: String? = null

    init {
        // A inicialização agora apenas prepara o ViewModel. A recolha de dados
        // é feita de forma segura dentro de uma coroutine.
        viewModelScope.launch {
            // Obtém o familyId e só depois começa a ouvir os produtos.
            getFamilyId()?.let { fId ->
                fetchProducts(fId)
            }
        }
    }

    /**
     * Obtém o familyId do utilizador. Se já o tivermos em cache, usa-o.
     * Se não, vai buscá-lo ao repositório. Esta é a função chave para evitar a race condition.
     */
    private suspend fun getFamilyId(): String? {
        if (familyId == null) {
            familyId = userRepository.getCurrentUser()?.familyId
        }
        return familyId
    }

    private fun fetchProducts(familyId: String) {
        db.collection("families").document(familyId).collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Erro ao ouvir os produtos da família.", e)
                    return@addSnapshotListener
                }
                _produtos.value = snapshots?.map { it.toObject(Product::class.java) } ?: emptyList()
            }
    }

    fun loadProduct(productId: String) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("products").document(productId)
                .get()
                .addOnSuccessListener { document ->
                    _selectedProduct.value = document.toObject(Product::class.java)
                }
        }
    }
    
    fun clearSelectedProduct(){
        _selectedProduct.value = null
    }

    fun addProduct(product: Product) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            val newProduct = product.copy(familiaId = fId)
            db.collection("families").document(fId).collection("products")
                .document(newProduct.id)
                .set(newProduct)
                .addOnSuccessListener { Log.d("InventarioViewModel", "Produto adicionado à família") }
        } ?: Log.e("InventarioViewModel", "FamilyID nulo. Não foi possível adicionar o produto.")
    }

    fun updateProduct(product: Product) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("products")
                .document(product.id)
                .set(product)
                .addOnSuccessListener { Log.d("InventarioViewModel", "Produto da família atualizado") }
        } ?: Log.e("InventarioViewModel", "FamilyID nulo. Não foi possível atualizar o produto.")
    }

    fun deleteProduct(productId: String) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("products")
                .document(productId)
                .delete()
                .addOnSuccessListener { Log.d("InventarioViewModel", "Produto da família apagado") }
        } ?: Log.e("InventarioViewModel", "FamilyID nulo. Não foi possível apagar o produto.")
    }
}
