package com.example.restock.data

import com.example.restock.model.BudgetSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/** HUGO MOREIRA - a22402246
 * Repositório responsável pelas operações de orçamento e histórico no Firestore.
 */
class BudgetRepository {

    private val db = FirebaseFirestore.getInstance()

    fun observeFamily(
        familyId: String,
        onChange: (com.example.restock.model.Family?) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId)
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObject(com.example.restock.model.Family::class.java))
            }

    fun observeProducts(
        familyId: String,
        onChange: (List<com.example.restock.model.Product>) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("products")
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObjects(com.example.restock.model.Product::class.java) ?: emptyList())
            }

    fun observeHistory(
        familyId: String,
        onChange: (List<BudgetSnapshot>) -> Unit
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("budgetHistory")
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObjects(BudgetSnapshot::class.java) ?: emptyList())
            }

    suspend fun saveSnapshot(familyId: String, docKey: String, snapshot: BudgetSnapshot) {
        db.collection("families").document(familyId)
            .collection("budgetHistory").document(docKey)
            .set(snapshot).await()
    }

    fun updateMonthlyBudget(familyId: String, budget: Double) =
        db.collection("families").document(familyId).update("monthlyBudget", budget)
}
