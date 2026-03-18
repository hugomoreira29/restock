package com.example.restock.ui.gamification

// AndroidX - ViewModel e scope para operações assíncronas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Modelo interno da aplicação
import com.example.restock.model.User

// Firebase - autenticação e base de dados em tempo real
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Coroutines - para expor os dados como fluxos observáveis pela interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Representa um emblema de gamificação com o seu estado de desbloqueio.
 * Os textos são referências a recursos de string para suporte a múltiplos idiomas.
 */

data class Badge(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val isUnlocked: Boolean
)

/** HUGO MOREIRA - a22402246
 * ViewModel responsável pela lógica de gamificação do utilizador.
 * Observa os dados do utilizador em tempo real e calcula automaticamente
 * quais os emblemas desbloqueados com base nos pontos e nível acumulados.
 */
class GamificationViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Fluxos de dados expostos à interface para observação
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _badges = MutableStateFlow<List<Badge>>(emptyList())
    val badges: StateFlow<List<Badge>> = _badges

    init {
        // Começa a observar o utilizador assim que o ViewModel é criado
        observeUser()
    }

    /**
     * Observa em tempo real o documento do utilizador no Firestore.
     * Sempre que os dados mudam, recalcula os emblemas desbloqueados.
     */
    private fun observeUser() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                _user.value = user
                user?.let { calculateBadges(it) }
            }
    }

    /**
     * Calcula a lista de emblemas e o estado de desbloqueio de cada um
     * com base nos pontos e nível atuais do utilizador.
     * Numa aplicação real, estes critérios poderiam vir do Firestore.
     */
    private fun calculateBadges(user: User) {
        viewModelScope.launch {
            val badgeList = listOf(
                Badge(
                    id = "inventory_master",
                    titleRes = com.example.restock.R.string.badge_inventory_master,
                    descriptionRes = com.example.restock.R.string.badge_inventory_master_desc,
                    isUnlocked = user.points >= 500  // Desbloqueado ao atingir 500 pontos
                ),
                Badge(
                    id = "shopper_pro",
                    titleRes = com.example.restock.R.string.badge_shopper_pro,
                    descriptionRes = com.example.restock.R.string.badge_shopper_pro_desc,
                    isUnlocked = user.points >= 200  // Desbloqueado ao atingir 200 pontos
                ),
                Badge(
                    id = "budget_king",
                    titleRes = com.example.restock.R.string.badge_budget_king,
                    descriptionRes = com.example.restock.R.string.badge_budget_king_desc,
                    isUnlocked = user.level >= 3     // Desbloqueado ao atingir o nível 3
                )
            )
            _badges.value = badgeList
        }
    }
}
