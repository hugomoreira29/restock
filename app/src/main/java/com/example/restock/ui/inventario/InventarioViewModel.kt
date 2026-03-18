package com.example.restock.ui.inventario

// Android - para logs de depuração
import android.util.Log

// AndroidX - ViewModel e scope para operações assíncronas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Classes internas da aplicação - repositório de gamificação e modelos
import com.example.restock.data.GamificationRepository
import com.example.restock.model.HistoryItem
import com.example.restock.model.Product
import com.example.restock.model.User

// Firebase - autenticação, base de dados, listeners e ordenação de queries
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Java - para gerar identificadores únicos para os itens do histórico
import java.util.UUID

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica de negócio do inventário familiar.
 * Observa em tempo real o utilizador, os produtos e o histórico no Firestore.
 * Gere todas as operações CRUD dos produtos e atribui pontos de gamificação
 * ao utilizador sempre que um novo produto é adicionado ao inventário.
 */
class InventarioViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gamificationRepository = GamificationRepository()

    // Fluxos de dados expostos à interface para observação
    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _familyName = MutableStateFlow<String>("")
    val familyName: StateFlow<String> = _familyName

    // ID da família atual, usado em todas as operações CRUD
    private var currentFamilyId: String? = null

    // Listeners do Firestore guardados para serem removidos quando necessário
    private var productsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    init {
        // Começa a observar o utilizador assim que o ViewModel é criado
        observeUserFamily()
    }

    /**
     * Observa o documento do utilizador no Firestore para detetar mudanças de família.
     * Sempre que o familyId muda, atualiza os listeners de produtos e histórico.
     */
    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen user failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    val newFamilyId = user?.familyId

                    // Só atualiza os listeners se o familyId realmente mudou
                    if (newFamilyId != currentFamilyId) {
                        currentFamilyId = newFamilyId
                        if (newFamilyId != null) {
                            fetchProducts(newFamilyId)
                            fetchFamilyName(newFamilyId)
                            fetchHistory(newFamilyId)
                        } else {
                            // Limpa todos os dados se o utilizador não pertencer a nenhuma família
                            _produtos.value = emptyList()
                            _history.value = emptyList()
                            _familyName.value = ""
                            productsListener?.remove()
                            historyListener?.remove()
                        }
                    }
                }
            }
    }

    /**
     * Obtém o nome da família a partir do Firestore e atualiza o fluxo correspondente.
     */
    private fun fetchFamilyName(familyId: String) {
        db.collection("families").document(familyId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    _familyName.value = document.getString("name") ?: ""
                }
            }
    }

    /**
     * Cria um listener em tempo real para a coleção de produtos da família.
     * Remove o listener anterior antes de criar um novo para evitar duplicados.
     */
    private fun fetchProducts(familyId: String) {
        productsListener?.remove()
        productsListener = db.collection("families").document(familyId).collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen products failed.", e)
                    return@addSnapshotListener
                }
                _produtos.value = snapshots?.map { it.toObject(Product::class.java) } ?: emptyList()
            }
    }

    /**
     * Cria um listener em tempo real para o histórico da família.
     * Ordena por data de consumo descendente e limita aos 20 registos mais recentes.
     */
    private fun fetchHistory(familyId: String) {
        historyListener?.remove()
        historyListener = db.collection("families").document(familyId).collection("history")
            .orderBy("dataConsumo", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("InventarioViewModel", "Listen history failed.", e)
                    return@addSnapshotListener
                }
                _history.value = snapshots?.map { it.toObject(HistoryItem::class.java) } ?: emptyList()
            }
    }

    /**
     * Carrega um produto específico pelo seu ID e atualiza o fluxo [selectedProduct].
     */
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

    /**
     * Limpa o produto selecionado no fluxo [selectedProduct].
     * Deve ser chamado ao sair do ecrã de edição.
     */
    fun clearSelectedProduct() {
        _selectedProduct.value = null
    }

    /**
     * Adiciona um novo produto ao inventário da família no Firestore.
     * Regista a operação no histórico e atribui 10 pontos de gamificação ao utilizador.
     */
    fun addProduct(product: Product) {
        val fId = currentFamilyId ?: return
        val newProduct = product.copy(familiaId = fId)

        db.collection("families").document(fId).collection("products")
            .document(newProduct.id)
            .set(newProduct)
            .addOnSuccessListener {
                Log.d("InventarioViewModel", "Product added to family $fId")
                addToHistory(newProduct)
                // Atribui pontos de gamificação por adicionar um produto
                viewModelScope.launch {
                    gamificationRepository.addPoints(10)
                }
            }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error adding document", e) }
    }

    /**
     * Regista um produto no histórico de consumo da família.
     * Cria um novo item com a data atual e guarda-o na sub-coleção "history".
     */
    private fun addToHistory(product: Product) {
        val fId = currentFamilyId ?: return
        val historyItem = HistoryItem(
            id = UUID.randomUUID().toString(),
            produto = product.nome,
            quantidade = product.quantidade,
            dataConsumo = System.currentTimeMillis(),
            familiaId = fId
        )
        db.collection("families").document(fId).collection("history")
            .document(historyItem.id)
            .set(historyItem)
            .addOnSuccessListener { Log.d("InventarioViewModel", "History item added") }
    }

    /**
     * Atualiza os dados de um produto existente no inventário da família.
     */
    fun updateProduct(product: Product) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("products")
            .document(product.id)
            .set(product)
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product updated in family $fId") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error updating document", e) }
    }

    /**
     * Elimina um produto do inventário da família pelo seu ID.
     */
    fun deleteProduct(productId: String) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("products")
            .document(productId)
            .delete()
            .addOnSuccessListener { Log.d("InventarioViewModel", "Product deleted from family $fId") }
            .addOnFailureListener { e -> Log.w("InventarioViewModel", "Error deleting document", e) }
    }

    /**
     * Remove os listeners do Firestore quando o ViewModel é destruído
     * para evitar fugas de memória e chamadas desnecessárias.
     */
    override fun onCleared() {
        super.onCleared()
        productsListener?.remove()
        historyListener?.remove()
    }
}
