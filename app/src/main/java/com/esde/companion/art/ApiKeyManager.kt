package com.esde.companion.art

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.esde.companion.CryptoManager
import com.esde.companion.data.ScraperCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_settings")

class ApiKeyManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val cryptoManager = CryptoManager(appContext)

    private val Context.dataStore by preferencesDataStore(name = "secure_settings")

    companion object {
        @Volatile
        private var INSTANCE: ApiKeyManager? = null

        fun getInstance(context: Context): ApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiKeyManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    // Define keys
    private val SGDB_KEY = stringPreferencesKey("sgdb_key")
    private val IGDB_ID = stringPreferencesKey("igdb_id")
    private val IGDB_SECRET = stringPreferencesKey("igdb_secret")

    val steamGridDbKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SGDB_KEY]?.let { cryptoManager.decrypt(it) }
    }

    val igdbId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[IGDB_ID]?.let { cryptoManager.decrypt(it) }
    }

    val igdbSecret: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[IGDB_SECRET]?.let { cryptoManager.decrypt(it) }
    }

    val scraperCredentials: Flow<ScraperCredentials> = context.dataStore.data
        .map { prefs ->
            ScraperCredentials(
                sgdbKey = prefs[SGDB_KEY]?.let { cryptoManager.decrypt(it) },
                igdbId = prefs[IGDB_ID]?.let { cryptoManager.decrypt(it) },
                igdbSecret = prefs[IGDB_SECRET]?.let { cryptoManager.decrypt(it) }
            )
        }

    suspend fun saveSteamGridDbKey(key: String) {
        val encrypted = cryptoManager.encrypt(key)
        appContext.dataStore.edit { prefs ->
            prefs[SGDB_KEY] = encrypted
        }
    }

    suspend fun saveIgdbCredentials(id: String, secret: String) {
        val encryptedId = cryptoManager.encrypt(id)
        appContext.dataStore.edit { prefs ->
            prefs[IGDB_ID] = encryptedId
        }

        val encryptedSecret = cryptoManager.encrypt(secret)
        appContext.dataStore.edit { prefs ->
            prefs[IGDB_SECRET] = encryptedSecret
        }
    }
}