package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A Activity principal da aplicação após o login.
 * Funciona como um contentor para todos os fragmentos principais (Início, Inventário, Lista, etc.)
 * e gere a navegação através da barra inferior (BottomNavigationView).
 */
class HomeActivity : AppCompatActivity() {

    // Controlador de Navegação que gere a troca entre fragmentos.
    private lateinit var navController: NavController
    // O botão de ação flutuante (o botão "+").
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Encontra o NavHostFragment no layout, que é a área onde os fragmentos são exibidos.
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Configura a barra de navegação inferior para funcionar com o NavController.
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        // Configura o botão de ação flutuante (FAB).
        fab = findViewById<FloatingActionButton>(R.id.fab_add)
        fab.setOnClickListener {
            showAddPopupMenu(it) // Mostra um menu flutuante quando o botão é clicado.
        }

        // Adiciona um listener para detetar mudanças de ecrã (fragmento).
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Controla a visibilidade do botão "+".
            if (destination.id == R.id.navigation_home) {
                fab.visibility = View.VISIBLE // Se estiver no ecrã de Início, mostra o botão.
            } else {
                fab.visibility = View.GONE // Em todos os outros ecrãs, esconde o botão.
            }
        }
    }

    /**
     * Cria e mostra um menu flutuante (PopupMenu) ancorado à view que foi clicada.
     * @param anchorView A View (neste caso, o FAB) onde o menu deve aparecer.
     */
    private fun showAddPopupMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        // Carrega o menu que definimos no ficheiro add_options_menu.xml.
        popupMenu.menuInflater.inflate(R.menu.add_options_menu, popupMenu.menu)

        // Define a ação a ser executada quando um item do menu é clicado.
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                // Se a opção "Adicionar ao Inventário" for clicada...
                R.id.option_add_to_inventory -> {
                    // Navega para o ecrã de adicionar produto ao inventário.
                    navController.navigate(R.id.action_global_to_adicionarProdutoFragment)
                    true
                }
                // Se a opção "Adicionar à Lista de Compras" for clicada...
                R.id.option_add_to_shopping_list -> {
                    // Navega para o ecrã de adicionar item à lista de compras.
                    navController.navigate(R.id.action_global_to_addShoppingItemFragment)
                    true
                }
                else -> false
            }
        }
        // Mostra o menu.
        popupMenu.show()
    }
}
