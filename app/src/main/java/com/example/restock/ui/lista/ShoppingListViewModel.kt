package com.example.restock.ui.lista

// Android - para logs de depuração
import android.util.Log

// AndroidX - ViewModel para gerir os dados do ecrã da lista de compras
import androidx.lifecycle.ViewModel

// Modelos internos da aplicação
import com.example.restock.model.ShoppingListItem
import com.example.restock.model.User

// Firebase - autenticação, base de dados e listener em tempo real
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica da lista de compras familiar.
 * Observa em tempo real o utilizador e os itens da lista no Firestore,
 * atualizando automaticamente a interface sempre que os dados mudam.
 */
class ShoppingListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Fluxo de dados com a lista de itens exposto à interface para observação
    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items

    // ID da família atual, usado em todas as operações CRUD
    private var currentFamilyId: String? = null

    // Listener do Firestore guardado para ser removido quando necessário
    private var shoppingListListener: ListenerRegistration? = null

    init {
        // Começa a observar o utilizador assim que o ViewModel é criado
        observeUserFamily()
    }

    /**
     * Observa o documento do utilizador no Firestore para detetar mudanças de família.
     * Sempre que o familyId muda, atualiza o listener da lista de compras.
     */
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

                    // Só atualiza o listener se o familyId realmente mudou
                    if (newFamilyId != currentFamilyId) {
                        currentFamilyId = newFamilyId
                        if (newFamilyId != null) {
                            fetchItems(newFamilyId)
                        } else {
                            // Limpa os dados se o utilizador não pertencer a nenhuma família
                            _items.value = emptyList()
                            shoppingListListener?.remove()
                        }
                    }
                }
            }
    }

    /**
     * Cria um listener em tempo real para a sub-coleção "shopping_list" da família.
     * Remove o listener anterior antes de criar um novo para evitar duplicados.
     */
    private fun fetchItems(familyId: String) {
        shoppingListListener?.remove()
        shoppingListListener = db.collection("families").document(familyId)
            .collection("shopping_list")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ShoppingListViewModel", "Listen items failed.", e)
                    return@addSnapshotListener
                }
                _items.value = snapshots?.map { it.toObject(ShoppingListItem::class.java) } ?: emptyList()
            }
    }

    /**
     * Adiciona um novo item à lista de compras da família no Firestore.
     */
    fun addItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId)
            .collection("shopping_list")
            .document(item.id)
            .set(item)
    }

    /**
     * Atualiza os dados de um item existente na lista de compras.
     * Utilizado para marcar ou desmarcar um item como comprado.
     */
    fun updateItem(item: ShoppingListItem) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId)
            .collection("shopping_list")
            .document(item.id)
            .set(item)
    }

    /**
     * Elimina um item da lista de compras da família pelo seu ID.
     */
    fun deleteItem(itemId: String) {
        val fId = currentFamilyId ?: return
        db.collection("families").document(fId)
            .collection("shopping_list")
            .document(itemId)
            .delete()
    }

    /**
     * Remove o listener do Firestore quando o ViewModel é destruído
     * para evitar fugas de memória e chamadas desnecessárias.
     */
    override fun onCleared() {
        super.onCleared()
        shoppingListListener?.remove()
    }
}
