package com.example.restock

// HUGO MOREIRA - a22402246

// Android - para suprimir o aviso de splash screen personalizada e navegação
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle

// AndroidX - para a Activity base e coroutines ligadas ao ciclo de vida
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

// Classes internas da aplicação - gestor de preferências e utilitários de tema
import com.example.restock.data.SettingsManager

// Firebase - autenticação para verificar se o utilizador tem sessão iniciada
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para ler as preferências de forma assíncrona antes de navegar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Activity de arranque da aplicação.
 * Apresenta um ecrã de carregamento enquanto aplica o idioma e o tema
 * guardados nas preferências e verifica se o utilizador já tem sessão iniciada.
 * Redireciona automaticamente para o HomeActivity ou LoginActivity consoante o estado.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val settingsManager = SettingsManager(this@SplashActivity)

            // Lê o idioma guardado no DataStore e aplica-o antes de continuar
            val languageCode = settingsManager.languageFlow.first()
            if (languageCode.isNotEmpty()) {
                ThemeUtils.applyAndSaveLocale(this@SplashActivity, languageCode)
            }

            // Lê o tema guardado no DataStore e aplica-o antes de continuar
            val themeCode = settingsManager.themeFlow.first()
            ThemeUtils.applySavedTheme(this@SplashActivity, themeCode)

            decideNextScreen()
        }
    }

    /**
     * Verifica o estado de autenticação do utilizador e navega para o ecrã adequado.
     * Se houver sessão iniciada, navega para o HomeActivity.
     * Caso contrário, navega para o LoginActivity.
     * A SplashActivity é terminada para impedir que o utilizador volte a ela.
     */
    private fun decideNextScreen() {
        // Verifica se a Activity ainda está válida antes de tentar navegar
        if (isFinishing) return

        val intent = if (FirebaseAuth.getInstance().currentUser != null) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}