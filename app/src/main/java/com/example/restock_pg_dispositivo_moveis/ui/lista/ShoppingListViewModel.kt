package com.example.restock_pg_dispositivo_moveis.ui.lista

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock_pg_dispositivo_moveis.data.UserRepository
import com.example.restock_pg_dispositivo_moveis.model.ShoppingListItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShoppingListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items

    private var familyId: String? = null

    init {
        viewModelScope.launch {
            getFamilyId()?.let { fId ->
                fetchItems(fId)
            }
        }
    }

    private suspend fun getFamilyId(): String? {
        if (familyId == null) {
            familyId = userRepository.getCurrentUser()?.familyId
        }
        return familyId
    }

    private fun fetchItems(familyId: String) {
        db.collection("families").document(familyId).collection("shopping_list")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ShoppingListViewModel", "Erro ao ouvir os itens da lista da família.", e)
                    return@addSnapshotListener
                }
                _items.value = snapshots?.map { it.toObject(ShoppingListItem::class.java) } ?: emptyList()
            }
    }

    fun addItem(item: ShoppingListItem) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("shopping_list")
                .document(item.id)
                .set(item)
        } ?: Log.e("ShoppingListViewModel", "FamilyID nulo. Não foi possível adicionar o item.")
    }

    fun updateItem(item: ShoppingListItem) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("shopping_list")
                .document(item.id)
                .set(item)
        } ?: Log.e("ShoppingListViewModel", "FamilyID nulo. Não foi possível atualizar o item.")
    }

    fun deleteItem(itemId: String) = viewModelScope.launch {
        getFamilyId()?.let { fId ->
            db.collection("families").document(fId).collection("shopping_list")
                .document(itemId)
                .delete()
        } ?: Log.e("ShoppingListViewModel", "FamilyID nulo. Não foi possível apagar o item.")
    }
}
