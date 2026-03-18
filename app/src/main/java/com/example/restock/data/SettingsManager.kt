package com.example.restock.data

// Android - contexto necessário para aceder ao DataStore
import android.content.Context

// DataStore - para persistência de preferências de forma assíncrona e segura
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Coroutines - para observar e atualizar as preferências de forma assíncrona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Cria uma instância única do DataStore associada ao contexto da aplicação,
// com o nome "settings" para identificar o ficheiro de preferências
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** HUGO MOREIRA - a22402246
 * SettingsManager.kt
 * Responsável por gerir as preferências da aplicação.
 * Guarda e lê configurações como notificações, idioma e tema,
 * utilizando o DataStore para persistência de dados local.
 */
class SettingsManager(context: Context) {

    // Instância do DataStore obtida a partir do contexto
    private val dataStore = context.dataStore

    companion object {
        // Chave para guardar o estado das notificações (ativadas ou desativadas)
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

        // Chave para guardar o idioma selecionado pelo utilizador
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")

        // Chave para guardar o tema selecionado pelo utilizador
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
    }

    /**
     * Fluxo que observa em tempo real o estado das notificações.
     * Devolve true por defeito caso a preferência ainda não tenha sido definida.
     */
    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[NOTIFICATIONS_ENABLED] ?: true
        }

    /**
     * Guarda o estado das notificações nas preferências.
     * Recebe true para ativar e false para desativar.
     */
    suspend fun setNotificationsEnabled(isEnabled: Boolean) {
        dataStore.edit {
            it[NOTIFICATIONS_ENABLED] = isEnabled
        }
    }

    /**
     * Fluxo que observa em tempo real o idioma selecionado pelo utilizador.
     * Devolve "system" por defeito caso nenhum idioma tenha sido definido.
     */
    val languageFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[SELECTED_LANGUAGE] ?: "system"
        }

    /**
     * Guarda o idioma selecionado pelo utilizador nas preferências.
     * Recebe o código do idioma, por exemplo "pt", "en", ou "system".
     */
    suspend fun setLanguage(languageCode: String) {
        dataStore.edit {
            it[SELECTED_LANGUAGE] = languageCode
        }
    }

    /**
     * Fluxo que observa em tempo real o tema selecionado pelo utilizador.
     * Devolve "system" por defeito caso nenhum tema tenha sido definido.
     */
    val themeFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[SELECTED_THEME] ?: "system"
        }

    /**
     * Guarda o tema selecionado pelo utilizador nas preferências.
     * Recebe o código do tema: "light" (claro), "dark" (escuro) ou "system" (padrão do sistema).
     */
    suspend fun setTheme(themeCode: String) {
        dataStore.edit {
            it[SELECTED_THEME] = themeCode
        }
    }
}