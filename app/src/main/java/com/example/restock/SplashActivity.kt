package com.example.restock

// HUGO MOREIRA - a22402246

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.restock.data.SettingsManager
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.initialize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializa o Firebase
        // Firebase.initialize(this)
        
        // Configura o App Check para modo DEBUG (Temporário para permitir login por SMS em modo de teste)
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("SplashActivity", "App Check inicializado em modo DEBUG")

        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val settingsManager = SettingsManager(this@SplashActivity)

            val languageCode = settingsManager.languageFlow.first()
            if (languageCode.isNotEmpty()) {
                ThemeUtils.applyAndSaveLocale(this@SplashActivity, languageCode)
            }

            val themeCode = settingsManager.themeFlow.first()
            ThemeUtils.applySavedTheme(this@SplashActivity, themeCode)

            decideNextScreen()
        }
    }

    private fun decideNextScreen() {
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