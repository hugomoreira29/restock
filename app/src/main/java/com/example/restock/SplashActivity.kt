package com.example.restock

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.restock.data.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A Activity de arranque da aplicação (Splash Screen).
 * Exibe um layout personalizado enquanto carrega as definições (como o idioma)
 * e verifica se o utilizador já tem sessão iniciada.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostra o Loading screen
        setContentView(R.layout.activity_splash)

        // Inicia a lógica de fundo para carregar e aplicar o idioma guardado.
        lifecycleScope.launch {
            val settingsManager = SettingsManager(this@SplashActivity)
            // Lê o código de idioma guardado no DataStore.
            val languageCode = settingsManager.languageFlow.first()
            
            // Se houver um idioma guardado, aplica-o à aplicação.
            if (languageCode.isNotEmpty()) {
                ThemeUtils.applyAndSaveLocale(this@SplashActivity, languageCode)
            }
            decideNextScreen()
        }
    }

    /**
     * Decide o proximo ecra
     */
    private fun decideNextScreen() {
        // Verifica se a activity ainda está válida antes de tentar navegar.
        if (isFinishing) {
            return
        }

        val auth = FirebaseAuth.getInstance()
        // Se houver um utilizador logado, vai para o Home.
        // Caso contrário, vai para a Login.
        val intent = if (auth.currentUser != null) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(intent)
        // Finaliza a SplashActivity para impedir que o utilizador volte a ela.
        finish()
    }
}
