package com.example.restock.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restock.R
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Badge(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val bgRes: Int,
    val iconTintRes: Int,
    val requirementRes: Int,
    val isUnlocked: Boolean
)

/** HUGO MOREIRA - a22402246 */
class GamificationViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _badges = MutableStateFlow<List<Badge>>(emptyList())
    val badges: StateFlow<List<Badge>> = _badges

    init {
        observeUser()
    }

    private fun observeUser() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                _user.value = user
                user?.let { calculateBadges(it) }
            }
    }

    private fun calculateBadges(user: User) {
        viewModelScope.launch {
            _badges.value = listOf(
                Badge(
                    id = "first_steps",
                    titleRes = R.string.badge_first_steps,
                    descriptionRes = R.string.badge_first_steps_desc,
                    iconRes = R.drawable.ic_home,
                    bgRes = R.drawable.bg_icon_primary,
                    iconTintRes = R.color.primary,
                    requirementRes = R.string.badge_req_first_steps,
                    isUnlocked = user.points >= 10
                ),
                Badge(
                    id = "active_pantry",
                    titleRes = R.string.badge_active_pantry,
                    descriptionRes = R.string.badge_active_pantry_desc,
                    iconRes = R.drawable.ic_inventory,
                    bgRes = R.drawable.bg_icon_primary,
                    iconTintRes = R.color.primary,
                    requirementRes = R.string.badge_req_active_pantry,
                    isUnlocked = user.points >= 50
                ),
                Badge(
                    id = "shopper_pro",
                    titleRes = R.string.badge_shopper_pro,
                    descriptionRes = R.string.badge_shopper_pro_desc,
                    iconRes = R.drawable.ic_list,
                    bgRes = R.drawable.bg_icon_secondary,
                    iconTintRes = R.color.secondary,
                    requirementRes = R.string.badge_req_shopper_pro,
                    isUnlocked = user.points >= 200
                ),
                Badge(
                    id = "inventory_master",
                    titleRes = R.string.badge_inventory_master,
                    descriptionRes = R.string.badge_inventory_master_desc,
                    iconRes = R.drawable.ic_inventory,
                    bgRes = R.drawable.bg_icon_primary,
                    iconTintRes = R.color.primary,
                    requirementRes = R.string.badge_req_inventory_master,
                    isUnlocked = user.points >= 500
                ),
                Badge(
                    id = "budget_king",
                    titleRes = R.string.badge_budget_king,
                    descriptionRes = R.string.badge_budget_king_desc,
                    iconRes = R.drawable.ic_budget,
                    bgRes = R.drawable.bg_icon_secondary,
                    iconTintRes = R.color.secondary,
                    requirementRes = R.string.badge_req_budget_king,
                    isUnlocked = user.level >= 3
                ),
                Badge(
                    id = "experienced_manager",
                    titleRes = R.string.badge_experienced_manager,
                    descriptionRes = R.string.badge_experienced_manager_desc,
                    iconRes = R.drawable.ic_settings,
                    bgRes = R.drawable.bg_icon_tertiary,
                    iconTintRes = R.color.tertiary,
                    requirementRes = R.string.badge_req_experienced_manager,
                    isUnlocked = user.level >= 5
                ),
                Badge(
                    id = "home_legend",
                    titleRes = R.string.badge_home_legend,
                    descriptionRes = R.string.badge_home_legend_desc,
                    iconRes = R.drawable.ic_gamification,
                    bgRes = R.drawable.bg_icon_tertiary,
                    iconTintRes = R.color.tertiary,
                    requirementRes = R.string.badge_req_home_legend,
                    isUnlocked = user.level >= 10
                )
            )
        }
    }
}
