package com.example.restock

// Android - para o contexto da aplicação
import android.content.Context

// AndroidX - para definir o idioma e gerir a lista de locales
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** HUGO MOREIRA - a22402246
 * Objeto utilitário responsável pela gestão do idioma e tema da aplicação.
 * Fornece funções para aplicar o idioma guardado nas preferências
 * a toda a aplicação através do sistema de localização do AndroidX.
 */
object ThemeUtils {

    /**
     * Aplica o idioma indicado a toda a aplicação.
     * Se o código for "system", repõe o idioma padrão do sistema operativo.
     * Caso contrário, aplica o idioma correspondente ao código fornecido (ex: "pt", "en").
     */
    fun applyAndSaveLocale(context: Context, languageCode: String) {
        val localeList = if (languageCode == "system") {
            // Repõe o idioma padrão do sistema operativo
            LocaleListCompat.getEmptyLocaleList()
        } else {
            // Cria uma lista de idiomas com o código fornecido
            LocaleListCompat.forLanguageTags(languageCode)
        }

        // Aplica o idioma a toda a aplicação através do AppCompatDelegate
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Aplica o tema da aplicação consoante o código fornecido.
     * "light" - tema claro | "dark" - tema escuro | "system" - segue o sistema.
     */
    fun applySavedTheme(context: Context, themeCode: String = "system") {
        val mode = when (themeCode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        // Aplica o tema a toda a aplicação
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
