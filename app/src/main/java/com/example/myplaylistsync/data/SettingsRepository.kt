package com.example.myplaylistsync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val remoteHost: String = "syncpl.local",
    val httpPort: String = "5001",
    val filePath: String = "playlist_esportate.txt",
    val localPathUri: String = "",
    val isPremium: Boolean = false
)

class SettingsRepository(val context: Context) {

    private object PreferencesKeys {
        val REMOTE_HOST = stringPreferencesKey("remote_host")
        val HTTP_PORT = stringPreferencesKey("http_port")
        val FILE_PATH = stringPreferencesKey("file_path")
        val LOCAL_PATH_URI = stringPreferencesKey("local_path_uri")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data
        .map { preferences ->
            Settings(
                remoteHost = preferences[PreferencesKeys.REMOTE_HOST] ?: "syncpl.local",
                httpPort = preferences[PreferencesKeys.HTTP_PORT] ?: "5001",
                filePath = preferences[PreferencesKeys.FILE_PATH] ?: "playlist_esportate.txt",
                localPathUri = preferences[PreferencesKeys.LOCAL_PATH_URI] ?: "",
                isPremium = preferences[PreferencesKeys.IS_PREMIUM] ?: false
            )
        }

    suspend fun updateSettings(settings: Settings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMOTE_HOST] = settings.remoteHost
            preferences[PreferencesKeys.HTTP_PORT] = settings.httpPort
            preferences[PreferencesKeys.FILE_PATH] = settings.filePath
            preferences[PreferencesKeys.LOCAL_PATH_URI] = settings.localPathUri
            preferences[PreferencesKeys.IS_PREMIUM] = settings.isPremium
        }
    }
}
