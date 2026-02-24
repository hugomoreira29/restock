package com.example.restock.ui.lista

// HUGO MOREIRA - a22402246

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.restock.model.ShoppingListItem
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ShoppingListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items

    private var currentFamilyId: String? = null
    private var shoppingListListener: ListenerRegistration? = null

    init {
        observeUserFamily()
    }

    private fun observeUserFamily() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ShoppingListViewModel", "Listen user failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    val newFamilyId = user?.familyId

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
    }

    private fun fetchItems(familyId: String) {
        shoppingListListener?.remove()
        shoppingListListener = db.collection("families").document(familyId).collection("shopping_list")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ShoppingListViewModel", "Listen items failed.", e)
                    return@addSnapshotListener
                }
                _items.value = snapshots?.map { it.toObject(ShoppingListItem::class.java) } ?: emptyList()
            }
    }

    fun addItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("shopping_list")
            .document(item.id)
            .set(item)
    }

    fun updateItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("shopping_list")
            .document(item.id)
            .set(item)
    }

    fun deleteItem(itemId: String) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId).collection("shopping_list")
            .document(itemId)
            .delete()
    }

    override fun onCleared() {
        super.onCleared()
        shoppingListListener?.remove()
    }
}
