package com.example.restock_pg_dispositivo_moveis

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala o ecrã de splash. Tem de ser chamado antes de super.onCreate().
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // A nova API de Splash Screen gere a exibição do ecrã de splash.
        // A transição de temas é gerida pelo `postSplashScreenTheme`.
        // Apenas precisamos de verificar o estado de login e redirecionar.

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // O utilizador não está logado, redireciona para a LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // O utilizador está logado, redireciona para a HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
        }

        // Finaliza a SplashActivity para impedir que o utilizador volte para ela
        finish()
    }
}