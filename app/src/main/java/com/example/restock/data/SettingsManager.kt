package com.example.restock.data

// HUGO MOREIRA - a22402246

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property para criar uma instância do DataStore a nível de aplicação
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        // Chave para a definição de notificações
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        // Chave para a definição de idioma
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
    }

    // Fluxo para observar o estado das notificações
    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            // Se a chave não existir, retorna true (notificações ativadas por defeito)
            preferences[NOTIFICATIONS_ENABLED] ?: true
        }

    // Função para guardar o estado das notificações
    suspend fun setNotificationsEnabled(isEnabled: Boolean) {
        dataStore.edit {
            it[NOTIFICATIONS_ENABLED] = isEnabled
        }
    }
    
    // Fluxo para observar o idioma selecionado
    val languageFlow: Flow<String> = dataStore.data
        .map { preferences ->
            // Se não houver idioma guardado, retorna "system" (padrão do sistema)
            preferences[SELECTED_LANGUAGE] ?: "system"
        }

    // Função para guardar o idioma selecionado
    suspend fun setLanguage(languageCode: String) {
        dataStore.edit {
            it[SELECTED_LANGUAGE] = languageCode
        }
    }
}
