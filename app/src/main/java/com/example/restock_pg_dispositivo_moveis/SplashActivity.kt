package com.example.restock_pg_dispositivo_moveis

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth

/**
 * A primeira Activity a ser aberta quando a aplicação inicia.
 * É responsável por exibir o ecrã de "splash" e decidir qual será o próximo ecrã a ser mostrado,
 * com base no estado de autenticação do utilizador.
 */
class SplashActivity : AppCompatActivity() {

    // Instância do Firebase Auth para verificar o utilizador.
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala e configura o splash screen da aplicação.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Inicializa o Firebase Auth.
        auth = FirebaseAuth.getInstance()

        // Verifica se há um utilizador atualmente autenticado.
        if (auth.currentUser != null) {
            // Se houver, navega diretamente para a HomeActivity.
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        } else {
            // Se não houver, navega para a LoginActivity para que o utilizador possa fazer login.
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
        // Finaliza a SplashActivity para que o utilizador não possa voltar a ela com o botão "back".
        finish()
    }
}
