package com.example.restock.ui.budget

// AndroidX - ViewModel para gerir os dados do ecrã de orçamento
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Modelos internos da aplicação
import com.example.restock.model.BudgetSnapshot
import com.example.restock.model.Family
import com.example.restock.model.Product
import com.example.restock.model.User

// Firebase - autenticação e base de dados em tempo real
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

// Java - para calcular o mês e ano atuais
import java.util.Calendar

/**
 * Classe auxiliar que representa os gastos totais de uma categoria.
 * Utilizada para alimentar o gráfico circular no BudgetFragment.
 */
data class CategorySpending(
    val category: String,
    val total: Double
)

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica de negócio do ecrã de orçamento.
 * Observa em tempo real os dados da família e dos produtos no Firestore,
 * calculando o total gasto e os gastos por categoria no mês atual.
 */
class BudgetViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Fluxos de dados expostos à interface para observação
    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent

    private val _categorySpending = MutableStateFlow<List<CategorySpending>>(emptyList())
    val categorySpending: StateFlow<List<CategorySpending>> = _categorySpending

    private val _budgetHistory = MutableStateFlow<List<BudgetSnapshot>>(emptyList())
    val budgetHistory: StateFlow<List<BudgetSnapshot>> = _budgetHistory

    private var familyId: String? = null
    private var snapshotWriteJob: Job? = null

    // Listeners do Firestore guardados para poderem ser removidos quando já não são necessários
    private var userListener: ListenerRegistration? = null
    private var familyListener: ListenerRegistration? = null
    private var productsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    init {
        // Começa a observar a família do utilizador assim que o ViewModel é criado
        observeUserFamily()
    }

    /**
     * Observa o documento do utilizador no Firestore para detetar mudanças na família.
     * Sempre que o familyId muda, remove os listeners antigos e cria novos.
     */
    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return

        userListener = db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            val newFamilyId = snapshot?.toObject(User::class.java)?.familyId

            // Só atualiza os listeners se o familyId realmente mudou
            if (newFamilyId != familyId) {
                familyId = newFamilyId
                familyListener?.remove()
                productsListener?.remove()

                if (newFamilyId != null) {
                    listenForBudgetData(newFamilyId)
                } else {
                    // Limpa os dados caso o utilizador não pertença a nenhuma família
                    _family.value = null
                    _totalSpent.value = 0.0
                    _categorySpending.value = emptyList()
                    _budgetHistory.value = emptyList()
                    historyListener?.remove()
                }
            }
        }
    }

    /**
     * Cria dois listeners em tempo real para a família indicada.
     * O primeiro observa as alterações ao documento da família (ex: orçamento).
     * O segundo observa as alterações à coleção de produtos (ex: adição ou remoção).
     */
    private fun listenForBudgetData(fId: String) {
        val familyDocRef = db.collection("families").document(fId)

        // Listener que deteta alterações no documento da família
        familyListener = familyDocRef.addSnapshotListener { familySnapshot, e ->
            if (e != null) { return@addSnapshotListener }
            _family.value = familySnapshot?.toObject(Family::class.java)
        }

        // Listener que deteta alterações na coleção de produtos e recalcula os gastos
        productsListener = familyDocRef.collection("products").addSnapshotListener { productsSnapshot, e ->
            if (e != null) { return@addSnapshotListener }
            val products = productsSnapshot?.toObjects(Product::class.java) ?: emptyList()
            calculateMonthlySpending(products)
        }

        // Listener em tempo real do histórico de orçamentos
        historyListener = familyDocRef.collection("budgetHistory")
            .addSnapshotListener { historySnapshot, e ->
                if (e != null) { return@addSnapshotListener }
                _budgetHistory.value = (historySnapshot?.toObjects(BudgetSnapshot::class.java) ?: emptyList())
                    .sortedWith(compareByDescending<BudgetSnapshot> { it.year }.thenByDescending { it.month })
            }
    }

    /**
     * Calcula o total gasto e os gastos por categoria no mês atual.
     * Filtra os produtos pelo mês e ano atuais com base na data de criação.
     * Guarda automaticamente um snapshot do mês no Firestore para o histórico.
     */
    private fun calculateMonthlySpending(products: List<Product>) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)   // 0-indexed
        val currentYear = calendar.get(Calendar.YEAR)

        val monthlyProducts = products.filter { product ->
            product.createdAt?.let {
                val productCalendar = Calendar.getInstance().apply { time = it }
                productCalendar.get(Calendar.MONTH) == currentMonth &&
                        productCalendar.get(Calendar.YEAR) == currentYear
            } ?: false
        }

        val total = monthlyProducts.sumOf { it.preco * it.quantidade }
        _totalSpent.value = total

        val categoryMap = monthlyProducts
            .groupBy { it.categoria.ifEmpty { "Outros" } }
            .mapValues { entry -> entry.value.sumOf { it.preco * it.quantidade } }

        _categorySpending.value = categoryMap.entries
            .map { (category, amount) -> CategorySpending(category, amount) }
            .sortedByDescending { it.total }

        // Debounce: cancela escrita anterior e espera 3s antes de escrever no Firestore,
        // evitando múltiplas escritas consecutivas quando vários produtos mudam ao mesmo tempo.
        val fId = familyId ?: return
        val docKey = "$currentYear-${String.format("%02d", currentMonth + 1)}"
        val snapshot = BudgetSnapshot(
            year = currentYear,
            month = currentMonth + 1,
            budget = _family.value?.monthlyBudget ?: 0.0,
            spent = total,
            categories = categoryMap
        )
        snapshotWriteJob?.cancel()
        snapshotWriteJob = viewModelScope.launch {
            delay(3_000)
            try {
                db.collection("families").document(fId)
                    .collection("budgetHistory").document(docKey)
                    .set(snapshot).await()
            } catch (e: Exception) {
                Log.w("BudgetViewModel", "Erro ao guardar snapshot do orçamento", e)
            }
        }
    }

    /**
     * Atualiza o orçamento mensal da família no Firestore.
     * Recebe o novo valor do orçamento definido pelo utilizador.
     */
    fun updateMonthlyBudget(newBudget: Double) {
        familyId?.let {
            db.collection("families").document(it)
                .update("monthlyBudget", newBudget)
        }
    }

    /**
     * Remove os listeners do Firestore quando o ViewModel é destruído
     * para evitar fugas de memória e chamadas desnecessárias.
     */
    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        familyListener?.remove()
        productsListener?.remove()
        historyListener?.remove()
    }
}
