package com.esde.companion

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class CryptoManager(context: Context) {
    init {
        AeadConfig.register()
    }

    private val keysetName = "master_keyset"
    private val prefFileName = "master_key_preference"
    private val masterKeyUri = "android-keystore://master_key"

    private val aead: Aead = AndroidKeysetManager.Builder()
        .withSharedPref(context, keysetName, prefFileName)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(masterKeyUri)
        .build()
        .getKeysetHandle()
        .getPrimitive(Aead::class.java)

    fun encrypt(data: String): String {
        val encrypted = aead.encrypt(data.toByteArray(), null)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
    }

    fun decrypt(encryptedData: String): String {
        val decoded = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
        return String(aead.decrypt(decoded, null))
    }
}