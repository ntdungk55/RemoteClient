package com.example.remoteclient.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class RememberedCredentialsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val storedValue = preferences.getString(CREDENTIALS_KEY, null) ?: return null
        val parts = storedValue.split(':', limit = 2)
        check(parts.size == 2) { "Stored credentials are invalid." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, decode(parts[0]))
        )
        val payload = JSONObject(cipher.doFinal(decode(parts[1])).toString(StandardCharsets.UTF_8))
        return Credentials(
            username = payload.getString(USERNAME_FIELD),
            password = payload.getString(PASSWORD_FIELD)
        )
    }

    fun save(username: String, password: String) {
        val payload = JSONObject()
            .put(USERNAME_FIELD, username)
            .put(PASSWORD_FIELD, password)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val storedValue = "${encode(cipher.iv)}:${encode(encrypted)}"
        check(preferences.edit().putString(CREDENTIALS_KEY, storedValue).commit()) {
            "Could not save remembered credentials."
        }
    }

    fun clear() {
        check(preferences.edit().remove(CREDENTIALS_KEY).commit()) {
            "Could not clear remembered credentials."
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encode(value: ByteArray): String =
        Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    data class Credentials(val username: String, val password: String)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "remote-client-credentials-key"
        const val PREFERENCES_NAME = "remote-credentials"
        const val CREDENTIALS_KEY = "encrypted-credentials"
        const val USERNAME_FIELD = "username"
        const val PASSWORD_FIELD = "password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
