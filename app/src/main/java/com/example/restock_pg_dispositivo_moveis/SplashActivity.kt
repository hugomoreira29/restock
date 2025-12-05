package com.example.restock_pg_dispositivo_moveis

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay de 2.5 segundos (2500 ms)
        Handler(Looper.getMainLooper()).postDelayed({
            val user = FirebaseAuth.getInstance().currentUser

            if (user == null) {
                // Não está logado → login
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                // Já está logado → home
                startActivity(Intent(this, HomeActivity::class.java))
            }

            finish()

        }, 2500)
    }
}
