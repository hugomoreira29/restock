package com.example.restock

// Android - para permissões, versão do sistema e vistas da activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View

// AndroidX - para permissões, navegação e WorkManager
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

// Worker de verificação de validade dos produtos
import com.example.restock.workers.ExpirationWorker

// Material Design - barra de navegação inferior e botão flutuante
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

// Java - para definir o intervalo diário do worker
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246
 * Activity principal da aplicação após o login do utilizador.
 * Alberga o NavHostFragment para a navegação entre fragmentos,
 * configura a barra de navegação inferior, gere o botão de ação
 * flutuante e agenda o worker de verificação de validade dos produtos.
 */
class HomeActivity : AppCompatActivity() {

    // Controlador que gere a navegação entre os diferentes ecrãs
    private lateinit var navController: NavController

    // Botão de ação flutuante para adicionar itens rapidamente
    private lateinit var fab: FloatingActionButton

    // Lançador para solicitar a permissão de envio de notificações
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // A permissão foi concedida: as notificações serão enviadas normalmente
        // A permissão foi negada: as notificações não serão apresentadas ao utilizador
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Obtém o NavHostFragment e o NavController para gerir a navegação
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Liga a barra de navegação inferior ao NavController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        // Configura o FAB e o seu menu flutuante de opções
        fab = findViewById(R.id.fab_add)
        fab.setOnClickListener { showAddPopupMenu(it) }

        // Mostra o FAB apenas no ecrã principal e oculta-o nos restantes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            fab.visibility = if (destination.id == R.id.navigation_home) View.VISIBLE else View.GONE
        }

        // Solicita a permissão de notificações e agenda o worker de verificação de validade
        askNotificationPermission()
        scheduleExpirationWorker()
    }

    /**
     * Verifica se a aplicação tem permissão para enviar notificações.
     * Solicita a permissão caso ainda não tenha sido concedida.
     * Apenas relevante para Android 13 (API 33) ou superior.
     */
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

    /**
     * Agenda o worker de verificação de validade dos produtos para correr uma vez por dia.
     * Usa um trabalho único periódico para evitar agendamentos duplicados.
     */
    private fun scheduleExpirationWorker() {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(
            1, TimeUnit.DAYS
        ).build()

        // KEEP mantém o worker já agendado caso exista, evitando duplicados
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            getString(R.string.expiration_check_periodic_work_name),
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    /**
     * Apresenta um menu flutuante ancorado ao FAB com as opções de adição disponíveis.
     * Permite navegar para adicionar um produto ao inventário ou à lista de compras.
     *
     * @param anchorView A vista à qual o menu ficará ancorado (o FAB).
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