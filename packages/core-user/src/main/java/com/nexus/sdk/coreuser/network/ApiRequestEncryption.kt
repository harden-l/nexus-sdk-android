package com.nexus.sdk.coreuser.network

import com.nexus.sdk.coreuser.init.CoreUserConfig
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiRequestEncryption {
    fun prepareBody(
        body: String,
        config: CoreUserConfig,
        encrypt: Boolean
    ): String {
        if (!encrypt) return body
        require(!config.encryptionKey.isNullOrBlank()) {
            "encryptionKey is required when encrypt=true"
        }
        return encrypt(body, config.encryptionKey)
    }

    fun readResponse(
        response: String,
        config: CoreUserConfig,
        encrypt: Boolean
    ): String {
        if (!encrypt || response.isBlank()) return response
        require(!config.encryptionKey.isNullOrBlank()) {
            "encryptionKey is required when encrypt=true"
        }
        return decrypt(response, config.encryptionKey)
    }

    fun encrypt(plainText: String, key: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), iv(key))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(cipherText: String, key: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(key), iv(key))
        val decoded = Base64.getDecoder().decode(cipherText.trim())
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    private fun secretKey(key: String): SecretKeySpec {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 32) {
            "encryptionKey must be 32 bytes for AES-256"
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun iv(key: String): IvParameterSpec {
        val ivBytes = key.take(16).toByteArray(Charsets.UTF_8)
        require(ivBytes.size == 16) {
            "encryptionKey must contain at least 16 bytes for AES-CBC IV"
        }
        return IvParameterSpec(ivBytes)
    }

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
}
