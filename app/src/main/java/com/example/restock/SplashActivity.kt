package com.example.restock

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        // Define o layout personalizado (com o logo e a barra de progresso) imediatamente.
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
        }

        // Adiciona um atraso de 2.5 segundos para exibir o splash screen e depois navega.
        // O Handler garante que a execução continua na Thread Principal.
        Handler(Looper.getMainLooper()).postDelayed({
            decideNextScreen()
        }, 2000)
    }

    /**
     * Decide qual o próximo ecrã a apresentar com base no estado de autenticação.
     */
    private fun decideNextScreen() {
        // Verifica se a activity ainda está válida antes de tentar navegar.
        if (isFinishing) {
            return
        }

        val auth = FirebaseAuth.getInstance()
        // Se houver um utilizador logado, vai para a HomeActivity.
        // Caso contrário, vai para a LoginActivity.
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
