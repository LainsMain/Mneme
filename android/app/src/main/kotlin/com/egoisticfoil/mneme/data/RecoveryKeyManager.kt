package com.egoisticfoil.mneme.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RecoveryKeyManager(context: Context) {
    private val preferences = context.getSharedPreferences("mneme_recovery", Context.MODE_PRIVATE)

    fun recoveryCode(): String {
        val key = keyBytes()
        return try {
            formatCode(key)
        } finally {
            key.fill(0)
        }
    }

    fun keyBytes(): ByteArray {
        preferences.getString(KEY_WRAPPED, null)?.let { stored ->
            return unwrap(stored)
        }
        val generated = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        store(generated, acknowledged = false)
        return generated
    }

    fun importRecoveryCode(code: String) {
        val key = parseCode(code)
        try {
            store(key, acknowledged = true)
        } finally {
            key.fill(0)
        }
    }

    fun needsAcknowledgement(): Boolean {
        keyBytes().fill(0)
        return !preferences.getBoolean(KEY_ACKNOWLEDGED, false)
    }

    fun acknowledge() {
        preferences.edit().putBoolean(KEY_ACKNOWLEDGED, true).apply()
    }

    private fun store(key: ByteArray, acknowledged: Boolean) {
        require(key.size == KEY_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val encrypted = cipher.doFinal(key)
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        preferences.edit()
            .putString(KEY_WRAPPED, value)
            .putBoolean(KEY_ACKNOWLEDGED, acknowledged)
            .apply()
    }

    private fun unwrap(stored: String): ByteArray {
        val pieces = stored.split('.', limit = 2)
        require(pieces.size == 2) { "The saved recovery key is damaged." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                wrappingKey(),
                GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)),
            )
        }
        return cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)).also {
            require(it.size == KEY_BYTES) { "The saved recovery key is invalid." }
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        internal fun formatCode(key: ByteArray): String =
            key.joinToString("") { "%02X".format(it.toInt() and 0xff) }.chunked(8).joinToString("-")

        internal fun parseCode(code: String): ByteArray {
            val normalized = code.filterNot { it == '-' || it.isWhitespace() }.uppercase()
            require(normalized.matches(Regex("[0-9A-F]{64}"))) {
                "Enter the complete 64-character Mneme recovery code."
            }
            return normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        private const val KEY_BYTES = 32
        private const val KEY_WRAPPED = "wrapped_recovery_key"
        private const val KEY_ACKNOWLEDGED = "recovery_key_acknowledged"
        private const val KEY_ALIAS = "mneme_recovery_key_wrapper_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
