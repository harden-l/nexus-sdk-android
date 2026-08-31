package com.nexus.sdk.coreuser.init

import java.util.Locale

data class CoreUserConfig(
    val appId: String = "",
    val productId: String,
    val productName: String,
    val accountName: String = DEFAULT_ACCOUNT_NAME,
    val apiBaseUrl: String,
    val version: String = DEFAULT_VERSION,
    val country: String = Locale.getDefault().country.ifBlank { DEFAULT_COUNTRY },
    val language: String = Locale.getDefault().language.ifBlank { DEFAULT_LANGUAGE },
    val encrypt: Boolean = true,
    val encryptionKey: String? = null,
    val debug: Boolean = false,
    val gt: Int? = null
) {
    init {
        require(productId.isNotBlank()) { "productId is required" }
        require(accountName.isNotBlank()) { "accountName is required" }
        require(apiBaseUrl.isNotBlank()) { "apiBaseUrl is required" }
        if (encrypt) {
            require(!encryptionKey.isNullOrBlank()) {
                "encryptionKey is required when encrypt=true"
            }
            require(encryptionKey.toByteArray(Charsets.UTF_8).size == 32) {
                "encryptionKey must be 32 bytes for AES-256"
            }
        }
    }

    private companion object {
        const val DEFAULT_VERSION = "1.0.0"
        const val DEFAULT_ACCOUNT_NAME = "test"
        const val DEFAULT_COUNTRY = "US"
        const val DEFAULT_LANGUAGE = "en"
    }
}
