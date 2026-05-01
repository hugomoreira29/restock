package com.example.restock.ui.inventario

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock.data.GamificationRepository
import com.example.restock.data.InventoryRepository
import com.example.restock.model.HistoryItem
import com.example.restock.model.Product
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica de negócio do inventário familiar.
 * Delega os acessos ao Firestore ao InventoryRepository, mantendo-se
 * focado apenas na gestão do estado e na lógica de negócio.
 */
class InventarioViewModel : ViewModel() {

    private val repository = InventoryRepository()
    private val auth = FirebaseAuth.getInstance()
    private val gamificationRepository = GamificationRepository()
    private val db = FirebaseFirestore.getInstance()

    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _familyName = MutableStateFlow<String>("")
    val familyName: StateFlow<String> = _familyName

    private val _pendingInvoiceProducts = MutableStateFlow<List<Product>>(emptyList())
    val pendingInvoiceProducts: StateFlow<List<Product>> = _pendingInvoiceProducts

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _hasMoreProducts = MutableStateFlow(false)
    val hasMoreProducts: StateFlow<Boolean> = _hasMoreProducts

    private var currentFamilyId: String? = null
    private var currentLimit = 50L

    private var userListener: ListenerRegistration? = null
    private var productsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    init {
        observeUserFamily()
    }

    fun setPendingInvoiceProducts(products: List<Product>) { _pendingInvoiceProducts.value = products }
    fun clearPendingInvoiceProducts() { _pendingInvoiceProducts.value = emptyList() }
    fun clearSelectedProduct() { _selectedProduct.value = null }
    fun clearError() { _error.value = null }

    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w("InventarioViewModel", "Listen user failed.", e); return@addSnapshotListener }
                val newFamilyId = snapshot?.toObject(User::class.java)?.familyId
                if (newFamilyId != currentFamilyId) {
                    currentFamilyId = newFamilyId
                    if (newFamilyId != null) {
                        fetchProducts(newFamilyId)
                        fetchFamilyName(newFamilyId)
                        fetchHistory(newFamilyId)
                    } else {
                        _produtos.value = emptyList()
                        _history.value = emptyList()
                        _familyName.value = ""
                        productsListener?.remove()
                        historyListener?.remove()
                    }
                }
            }
    }

    private fun fetchFamilyName(familyId: String) {
        repository.getFamilyName(familyId).addOnSuccessListener { doc ->
            _familyName.value = doc.getString("name") ?: ""
        }
    }

    private fun fetchProducts(familyId: String) {
        productsListener?.remove()
        productsListener = repository.observeProducts(
            familyId = familyId,
            limit = currentLimit,
            onChange = { list ->
                _produtos.value = list
                _hasMoreProducts.value = list.size.toLong() == currentLimit
            },
            onError = { e -> Log.w("InventarioViewModel", "Listen products failed.", e) }
        )
    }

    fun loadMoreProducts() {
        val fId = currentFamilyId ?: return
        currentLimit += 50
        fetchProducts(fId)
    }

    private fun fetchHistory(familyId: String) {
        historyListener?.remove()
        historyListener = repository.observeHistory(
            familyId = familyId,
            onChange = { _history.value = it },
            onError = { e -> Log.w("InventarioViewModel", "Listen history failed.", e) }
        )
    }

    fun loadProduct(productId: String) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("products").document(productId)
            .get()
            .addOnSuccessListener { _selectedProduct.value = it.toObject(Product::class.java) }
            .addOnFailureListener { _selectedProduct.value = null }
    }

    fun addProduct(product: Product) {
        val fId = currentFamilyId ?: return
        val newProduct = product.copy(familiaId = fId)
        repository.addProduct(fId, newProduct)
            .addOnSuccessListener {
                addToHistory(newProduct)
                viewModelScope.launch { gamificationRepository.addPoints(10) }
            }
            .addOnFailureListener { e ->
                Log.w("InventarioViewModel", "Error adding product", e)
                _error.value = e.message
            }
    }

    fun restoreProduct(product: Product) {
        val fId = currentFamilyId ?: return
        repository.updateProduct(fId, product)
            .addOnFailureListener { e ->
                Log.w("InventarioViewModel", "Error restoring product", e)
                _error.value = e.message
            }
    }

    fun updateProduct(product: Product) {
        val fId = currentFamilyId ?: return
        repository.updateProduct(fId, product)
            .addOnFailureListener { e ->
                Log.w("InventarioViewModel", "Error updating product", e)
                _error.value = e.message
            }
    }

    fun deleteProduct(productId: String) {
        val fId = currentFamilyId ?: return
        repository.deleteProduct(fId, productId)
            .addOnFailureListener { e ->
                Log.w("InventarioViewModel", "Error deleting product", e)
                _error.value = e.message
            }
    }

    private fun addToHistory(product: Product) {
        val fId = currentFamilyId ?: return
        val item = HistoryItem(
            id = UUID.randomUUID().toString(),
            produto = product.nome,
            quantidade = product.quantidade,
            dataConsumo = System.currentTimeMillis(),
            familiaId = fId
        )
        repository.addToHistory(fId, item)
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        productsListener?.remove()
        historyListener?.remove()
    }
}
