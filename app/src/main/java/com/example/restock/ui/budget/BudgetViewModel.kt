package com.example.restock.ui.budget

// AndroidX - ViewModel para gerir os dados do ecrã de orçamento
import androidx.lifecycle.ViewModel

// Modelos internos da aplicação
import com.example.restock.model.Family
import com.example.restock.model.Product
import com.example.restock.model.User

// Firebase - autenticação e base de dados em tempo real
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private var familyId: String? = null

    // Listeners do Firestore guardados para poderem ser removidos quando já não são necessários
    private var familyListener: ListenerRegistration? = null
    private var productsListener: ListenerRegistration? = null

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

        db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
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
    }

    /**
     * Calcula o total gasto e os gastos por categoria no mês atual.
     * Filtra os produtos pelo mês e ano atuais com base na data de criação.
     */
    private fun calculateMonthlySpending(products: List<Product>) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Filtra apenas os produtos criados no mês e ano atuais
        val monthlyProducts = products.filter { product ->
            product.createdAt?.let {
                val productCalendar = Calendar.getInstance().apply { time = it }
                productCalendar.get(Calendar.MONTH) == currentMonth &&
                        productCalendar.get(Calendar.YEAR) == currentYear
            } ?: false
        }

        // Calcula o total gasto somando preço * quantidade de cada produto
        _totalSpent.value = monthlyProducts.sumOf { it.preco * it.quantidade }

        // Agrupa os produtos por categoria e calcula o total gasto em cada uma
        _categorySpending.value = monthlyProducts
            .groupBy { it.categoria.ifEmpty { "Outros" } }
            .map { (category, productList) ->
                CategorySpending(category, productList.sumOf { it.preco * it.quantidade })
            }
            .sortedByDescending { it.total }
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
        familyListener?.remove()
        productsListener?.remove()
    }
}
