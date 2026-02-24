package com.example.restock

// HUGO MOREIRA - a22402246

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object ThemeUtils {

    fun applyAndSaveLocale(context: Context, languageCode: String) {
        val localeList = if (languageCode == "system") {
            // Usa a lista de idiomas padrão do sistema
            LocaleListCompat.getEmptyLocaleList()
        } else {
            // Cria uma lista de idiomas com o código fornecido
            LocaleListCompat.forLanguageTags(languageCode)
        }

        // Define o locale para toda a aplicação
        AppCompatDelegate.setApplicationLocales(localeList)
    }
    
    // Função auxiliar que pode ser usada no futuro se precisarmos apenas de aplicar o tema
    // sem mexer no idioma.
    fun applySavedTheme(context: Context) {
        // TODO: Implementar lógica de tema (ex: Dark/Light) se necessário.
    }
}
