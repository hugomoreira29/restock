package com.example.restock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.restock.workers.BudgetReminderWorker
import com.example.restock.workers.ExpirationWorker
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246 */
class HomeActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var fab: FloatingActionButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Listeners Firestore para pedidos de adesão à família
    private var userListenerReg: ListenerRegistration? = null
    private var familyListenerReg: ListenerRegistration? = null

    // -1 = ainda não inicializado (primeira leitura não gera notificação)
    private var previousPendingCount = -1

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permissão concedida ou negada pelo utilizador */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        fab = findViewById(R.id.fab_add)
        fab.setOnClickListener { showAddPopupMenu(it) }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            fab.visibility = if (destination.id == R.id.navigation_home) View.VISIBLE else View.GONE
        }

        askNotificationPermission()
        createNotificationChannels()
        scheduleExpirationWorker()
        scheduleBudgetReminderWorker()
        setupFamilyJoinRequestListener()
    }

    /**
     * Cria os canais de notificação necessários (Android 8+).
     * Chamado no arranque para garantir que os canais existem antes de enviar notificações.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FAMILY,
                    getString(R.string.notif_family_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.notif_family_channel_desc)
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_EXPIRY,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_channel_desc)
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    BudgetReminderWorker.CHANNEL_ID,
                    getString(R.string.notif_budget_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_budget_channel_desc)
                }
            )
        }
    }

    /**
     * Observa em tempo real os pedidos de adesão pendentes na família do utilizador.
     * Envia uma notificação imediata ao admin sempre que aparece um novo pedido.
     * A primeira leitura é ignorada para não notificar de pedidos já existentes.
     */
    private fun setupFamilyJoinRequestListener() {
        val userId = auth.currentUser?.uid ?: return

        userListenerReg = db.collection("users").document(userId)
            .addSnapshotListener { userSnap, _ ->
                val newFamilyId = userSnap?.getString("familyId")

                // Sem família — remove o listener da família anterior e reset
                if (newFamilyId == null) {
                    familyListenerReg?.remove()
                    familyListenerReg = null
                    previousPendingCount = -1
                    return@addSnapshotListener
                }

                // Reagista o listener sempre que a família muda
                familyListenerReg?.remove()
                previousPendingCount = -1

                familyListenerReg = db.collection("families").document(newFamilyId)
                    .addSnapshotListener { famSnap, _ ->
                        val roles = famSnap?.get("roles") as? Map<*, *> ?: return@addSnapshotListener
                        if (roles[userId] != "Admin") return@addSnapshotListener

                        val pending = famSnap.get("pendingMembers") as? List<*> ?: emptyList<Any>()
                        val currentCount = pending.size

                        when {
                            previousPendingCount == -1 -> {
                                // Primeira leitura: guarda o estado atual sem notificar
                                previousPendingCount = currentCount
                            }
                            currentCount > previousPendingCount -> {
                                // Novo pedido chegou — notifica
                                sendFamilyJoinNotification(currentCount)
                                previousPendingCount = currentCount
                            }
                            else -> previousPendingCount = currentCount
                        }
                    }
            }
    }

    private fun sendFamilyJoinNotification(count: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val text = if (count == 1) getString(R.string.notif_family_single)
                   else getString(R.string.notif_family_multiple, count)

        val notification = NotificationCompat.Builder(this, CHANNEL_FAMILY)
            .setSmallIcon(R.drawable.ic_family)
            .setContentTitle(getString(R.string.notif_family_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIF_ID_FAMILY, notification)
    }

    /**
     * Agenda o worker de verificação de validade para correr 2x por dia (a cada 12h).
     * UPDATE garante que qualquer agendamento anterior é atualizado com os novos parâmetros.
     */
    private fun scheduleExpirationWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(
            12, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            getString(R.string.expiration_check_periodic_work_name),
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleBudgetReminderWorker() {
        val workRequest = PeriodicWorkRequestBuilder<BudgetReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "BudgetReminderPeriodic",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showAddPopupMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.add_options_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.option_add_to_inventory -> {
                    navController.navigate(R.id.action_global_to_adicionarProdutoFragment)
                    true
                }
                R.id.option_add_to_shopping_list -> {
                    navController.navigate(R.id.action_global_to_addShoppingItemFragment)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        userListenerReg?.remove()
        familyListenerReg?.remove()
    }

    companion object {
        private const val CHANNEL_FAMILY = "family_channel"
        private const val CHANNEL_EXPIRY = "expiration_channel"
        private const val NOTIF_ID_FAMILY = 1002
    }
}
