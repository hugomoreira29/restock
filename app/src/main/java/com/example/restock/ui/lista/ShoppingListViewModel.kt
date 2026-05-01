package com.example.restock.ui.lista

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.restock.data.ShoppingListRepository
import com.example.restock.model.ShoppingListItem
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica da lista de compras familiar.
 * Delega os acessos ao Firestore ao ShoppingListRepository.
 */
class ShoppingListViewModel : ViewModel() {

    private val repository = ShoppingListRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentFamilyId: String? = null

    private var userListener: ListenerRegistration? = null
    private var shoppingListListener: ListenerRegistration? = null

    init {
        observeUserFamily()
    }

    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w("ShoppingListViewModel", "Listen user failed.", e); return@addSnapshotListener }
                val newFamilyId = snapshot?.toObject(User::class.java)?.familyId
                if (newFamilyId != currentFamilyId) {
                    currentFamilyId = newFamilyId
                    if (newFamilyId != null) {
                        fetchItems(newFamilyId)
                    } else {
                        _items.value = emptyList()
                        shoppingListListener?.remove()
                    }
                }
            }
    }

    private fun fetchItems(familyId: String) {
        shoppingListListener?.remove()
        shoppingListListener = repository.observeItems(
            familyId = familyId,
            onChange = { _items.value = it },
            onError = { e -> Log.w("ShoppingListViewModel", "Listen items failed.", e) }
        )
    }

    fun addItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        repository.addItem(fId, item).addOnFailureListener { e -> _error.value = e.message }
    }

    fun updateItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        val finalItem = when {
            item.isChecked && item.boughtBy == null -> {
                val name = auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "?"
                item.copy(boughtBy = name)
            }
            !item.isChecked -> item.copy(boughtBy = null)
            else -> item
        }
        repository.updateItem(fId, finalItem).addOnFailureListener { e -> _error.value = e.message }
    }

    fun deleteItem(itemId: String) {
        val fId = currentFamilyId ?: return
        repository.deleteItem(fId, itemId).addOnFailureListener { e -> _error.value = e.message }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        shoppingListListener?.remove()
    }
}
