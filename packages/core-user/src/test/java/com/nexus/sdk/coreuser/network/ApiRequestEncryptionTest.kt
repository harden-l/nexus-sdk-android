package com.nexus.sdk.coreuser.network

import com.nexus.sdk.coreuser.init.CoreUserConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiRequestEncryptionTest {
    @Test
    fun returnsPlainBodyWhenEncryptionDisabled() {
        val body = """{"uid":"user"}"""

        val prepared = ApiRequestEncryption.prepareBody(
            body = body,
            config = config(encrypt = false),
            encrypt = false
        )

        assertEquals(body, prepared)
    }

    @Test
    fun encryptsWithAesCbcPkcs5Padding() {
        val body = """{"status":1,"hotUpdateUrl":""}"""
        val key = "1b8df48c1fa64ce28a2e8133dffe600c"

        val encrypted = ApiRequestEncryption.encrypt(body, key)

        assertEquals("n+JgfkzArtm/JTkQQj1tZa6pMAucAqMU2RqMZ/6uq3o=", encrypted)
        assertEquals(body, ApiRequestEncryption.decrypt(encrypted, key))
    }

    private fun config(
        encrypt: Boolean,
        encryptionKey: String? = if (encrypt) "1b8df48c1fa64ce28a2e8133dffe600c" else null
    ): CoreUserConfig {
        return CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "https://example.com",
            encrypt = encrypt,
            encryptionKey = encryptionKey
        )
    }
}
