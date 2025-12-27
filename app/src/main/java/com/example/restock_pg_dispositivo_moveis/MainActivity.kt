package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * NOTA: Esta é a Activity principal que é criada por defeito pelo Android Studio.
 * No nosso projeto, a sua funcionalidade foi substituída pela arquitetura com SplashActivity -> HomeActivity.
 * Este ficheiro e o seu layout (activity_main.xml) podem ser removidos do projeto para o manter limpo.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Função chamada quando a Activity é criada pela primeira vez.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica o tema e o idioma que foram guardados nas preferências.
        ThemeUtils.applySavedThemeAndLocale(this)
        // Chama a implementação da superclasse.
        super.onCreate(savedInstanceState)
        // Define o layout da Activity, ligando-a ao ficheiro activity_main.xml.
        setContentView(R.layout.activity_main)
    }
}
