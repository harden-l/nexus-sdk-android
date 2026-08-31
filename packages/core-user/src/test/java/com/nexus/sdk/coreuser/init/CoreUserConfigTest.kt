package com.nexus.sdk.coreuser.init

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreUserConfigTest {
    @Test
    fun createsConfigWithIntegrationDefaults() {
        val config = CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encryptionKey = TEST_ENCRYPTION_KEY
        )

        assertEquals(true, config.encrypt)
        assertEquals("", config.appId)
        assertEquals("1.0.0", config.version)
        assertEquals("TEST PRODUCT", config.productName)
        assertEquals("7", config.productId)
        assertEquals("test", config.accountName)
        assertEquals(true, config.country.isNotBlank())
        assertEquals(true, config.language.isNotBlank())
        assertEquals(null, config.gt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankAccountName() {
        CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = " ",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encrypt = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankApiBaseUrl() {
        CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "",
            encryptionKey = TEST_ENCRYPTION_KEY
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingEncryptionKeyWhenEncryptionEnabled() {
        CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encrypt = true
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidEncryptionKeyLengthWhenEncryptionEnabled() {
        CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encrypt = true,
            encryptionKey = "short-key"
        )
    }

    @Test
    fun allowsMissingEncryptionKeyWhenEncryptionDisabled() {
        val config = CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encrypt = false
        )

        assertEquals(false, config.encrypt)
        assertEquals(null, config.encryptionKey)
    }

    @Test
    fun acceptsOptionalGrantTierCode() {
        val config = CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
            encrypt = false,
            gt = 2
        )

        assertEquals(2, config.gt)
    }

    private companion object {
        const val TEST_ENCRYPTION_KEY = "1b8df48c1fa64ce28a2e8133dffe600c"
    }
}
