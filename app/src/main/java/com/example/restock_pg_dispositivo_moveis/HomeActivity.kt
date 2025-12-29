package com.example.restock_pg_dispositivo_moveis

// HUGO MOREIRA - a22402246

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.restock_pg_dispositivo_moveis.workers.ExpirationWorker
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.TimeUnit

/**
 * Activity principal após o login do utilizador.
 * É responsável por albergar o NavHostFragment (onde os fragmentos são exibidos) e configurar
 * a navegação da barra inferior (BottomNavigationView).
 */
class HomeActivity : AppCompatActivity() {

    // Controlador que gere a navegação entre os diferentes ecrãs (fragmentos).
    private lateinit var navController: NavController
    // Botão de Ação Flutuante (FAB) para ações rápidas como adicionar itens.
    private lateinit var fab: FloatingActionButton

    // Launcher que lida com o pedido de permissão para enviar notificações
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // A permissão foi concedida pelo utilizador.
        } else {
            // A permissão foi negada. As notificações não serão exibidas.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Encontra o NavHostFragment, que é o contentor onde os fragmentos são carregados.
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Liga a barra de navegação inferior ao NavController.
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        // Configura o FAB e o seu listener de clique.
        fab = findViewById<FloatingActionButton>(R.id.fab_add)
        fab.setOnClickListener { showAddPopupMenu(it) }

        // Listener que observa as mudanças de ecrã para mostrar ou esconder o FAB.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // O FAB só é visível no ecrã principal (Home).
            fab.visibility = if (destination.id == R.id.navigation_home) View.VISIBLE else View.GONE
        }
        
        // Pede a permissão de notificação (se necessário) e agenda a tarefa de fundo.
        askNotificationPermission()
        scheduleExpirationWorker()
    }

    /**
     * Verifica se a app tem permissão para enviar notificações e, se não tiver, pede-a.
     * Esta verificação só é relevante para o Android 13 (API 33) e superior.
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Agenda uma tarefa de fundo (worker) que corre periodicamente para verificar a validade dos produtos.
     */
    private fun scheduleExpirationWorker() {
        // --- TRABALHO PERIÓDICO (PARA PRODUÇÃO) ---
        val periodicWorkRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(
            1, TimeUnit.DAYS // Corre aproximadamente uma vez por dia.
        ).build()

        // Usa enqueueUniquePeriodicWork para garantir que a tarefa não é agendada múltiplas vezes.
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "ExpirationCheckPeriodic",
            ExistingPeriodicWorkPolicy.KEEP, // Mantém a tarefa agendada se já existir.
            periodicWorkRequest
        )
    }

    /**
     * Mostra um menu flutuante (PopupMenu) quando o FAB é clicado.
     * @param anchorView A View a que o menu ficará "ancorado" (o FAB).
     */
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
}
