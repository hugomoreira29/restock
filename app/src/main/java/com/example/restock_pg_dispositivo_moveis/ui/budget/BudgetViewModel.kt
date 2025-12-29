package com.example.restock_pg_dispositivo_moveis.ui.budget

// HUGO MOREIRA - a22402246

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.restock_pg_dispositivo_moveis.model.Family
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

// Classe auxiliar para a UI do gráfico
data class CategorySpending(
    val category: String,
    val total: Double
)

class BudgetViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent

    private val _categorySpending = MutableStateFlow<List<CategorySpending>>(emptyList())
    val categorySpending: StateFlow<List<CategorySpending>> = _categorySpending

    private var familyId: String? = null
    // Vamos precisar de dois listeners: um para a família, outro para os produtos.
    private var familyListener: ListenerRegistration? = null
    private var productsListener: ListenerRegistration? = null

    init {
        observeUserFamily()
    }

    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            val newFamilyId = snapshot?.toObject(User::class.java)?.familyId
            if (newFamilyId != familyId) {
                familyId = newFamilyId
                // Remove listeners antigos antes de criar novos
                familyListener?.remove()
                productsListener?.remove()

                if (newFamilyId != null) {
                    listenForBudgetData(newFamilyId)
                } else {
                    _family.value = null
                    _totalSpent.value = 0.0
                    _categorySpending.value = emptyList()
                }
            }
        }
    }

    private fun listenForBudgetData(fId: String) {
        val familyDocRef = db.collection("families").document(fId)

        // Listener #1: Ouve alterações no documento da família (ex: mudança de nome ou orçamento)
        familyListener = familyDocRef.addSnapshotListener { familySnapshot, e ->
            if (e != null) { return@addSnapshotListener }
            _family.value = familySnapshot?.toObject(Family::class.java)
        }

        // Listener #2: Ouve alterações na coleção de produtos (adição, remoção, etc.)
        productsListener = familyDocRef.collection("products").addSnapshotListener { productsSnapshot, e ->
            if (e != null) { return@addSnapshotListener }
            val products = productsSnapshot?.toObjects(Product::class.java) ?: emptyList()
            calculateMonthlySpending(products)
        }
    }

    private fun calculateMonthlySpending(products: List<Product>) {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val monthlyProducts = products.filter { product ->
            product.createdAt?.let {
                val productCalendar = Calendar.getInstance().apply { time = it }
                productCalendar.get(Calendar.MONTH) == currentMonth && 
                productCalendar.get(Calendar.YEAR) == currentYear
            } ?: false
        }

        _totalSpent.value = monthlyProducts.sumOf { it.preco * it.quantidade }

        _categorySpending.value = monthlyProducts
            .groupBy { it.categoria.ifEmpty { "Outros" } }
            .map { (category, productList) ->
                CategorySpending(category, productList.sumOf { it.preco * it.quantidade })
            }
            .sortedByDescending { it.total }
    }
    
    fun updateMonthlyBudget(newBudget: Double) {
        familyId?.let {
            db.collection("families").document(it)
                .update("monthlyBudget", newBudget)
        }
    }

    override fun onCleared() {
        super.onCleared()
        familyListener?.remove()
        productsListener?.remove()
    }
}
