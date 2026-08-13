package com.phoneagent.data

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun put(alias: String, value: CharArray)
    fun get(alias: String): CharArray?
    fun remove(alias: String)
    fun contains(alias: String): Boolean
}

class EncryptedSecretStore(context: Context) : SecretStore {
    private val preferences = context.getSharedPreferences("encrypted_provider_secrets", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun put(alias: String, value: CharArray) {
        val plaintext = value.concatToString().encodeToByteArray()
        value.fill('\u0000')
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(plaintext)
            val encoded = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
            check(preferences.edit().putString(alias, encoded).commit()) { "Unable to persist secret" }
        } finally {
            plaintext.fill(0)
        }
    }

    override fun get(alias: String): CharArray? {
        val encoded = preferences.getString(alias, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_BYTES) { "Invalid encrypted secret" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, IV_BYTES)))
        val clear = cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
        return try {
            clear.decodeToString().toCharArray()
        } finally {
            clear.fill(0)
            payload.fill(0)
        }
    }

    override fun remove(alias: String) {
        preferences.edit().remove(alias).apply()
    }

    override fun contains(alias: String): Boolean = preferences.contains(alias)

    private fun key(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance("AES", KEYSTORE).apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "phoneagent-provider-secrets-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}

