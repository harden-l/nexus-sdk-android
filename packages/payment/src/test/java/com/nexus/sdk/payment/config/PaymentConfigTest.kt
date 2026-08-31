package com.nexus.sdk.payment.config

import com.nexus.sdk.payment.config.PaymentConfig
import com.nexus.sdk.payment.config.PaymentConfigResolver
import com.nexus.sdk.payment.config.PaymentContext
import com.nexus.sdk.payment.config.PaymentRule
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.subscription_template.SubscriptionPageConfig
import com.nexus.sdk.payment.subscription_template.SubscriptionPageConfigParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentConfigTest {
    @Test
    fun createsDefaultAndroidConfig() {
        val config = PaymentConfig(productId = "product_a")

        assertEquals("android", config.platform)
        assertEquals(PaymentChannel.GOOGLE_PLAY, config.defaultChannel)
        assertEquals(listOf(PaymentChannel.GOOGLE_PLAY), config.enabledChannels)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyChannels() {
        PaymentConfig(productId = "product_a", enabledChannels = emptyList())
    }

    @Test
    fun resolvesMatchedRule() {
        val config = PaymentConfig(
            productId = "product_a",
            enabledChannels = listOf(PaymentChannel.GOOGLE_PLAY),
            rules = listOf(
                PaymentRule(
                    country = "BR",
                    platform = "android",
                    minVersion = "1.2.0",
                    enabledChannels = listOf(PaymentChannel.GOOGLE_PLAY, PaymentChannel.STRIPE),
                    defaultChannel = PaymentChannel.STRIPE,
                    fallbackChannels = listOf(PaymentChannel.GOOGLE_PLAY)
                )
            )
        )

        val resolved = PaymentConfigResolver.resolve(
            config,
            PaymentContext(country = "BR", platform = "android", appVersion = "1.2.1")
        )

        assertEquals(PaymentChannel.STRIPE, resolved.defaultChannel)
        assertEquals(listOf(PaymentChannel.GOOGLE_PLAY, PaymentChannel.STRIPE), resolved.enabledChannels)
    }

    @Test
    fun parsesSubscriptionPageConfig() {
        val pageConfig = SubscriptionPageConfigParser.parse(
            """
                {
                  "template_id": "default_subscription_v1",
                  "scene": "app_start",
                  "title": "Upgrade to Pro",
                  "benefit_description": "Purchase one product and get VIP access to four other products.",
                  "benefits": ["Unlimited usage", "Remove ads"],
                  "shared_apps": {
                    "title": "membership share",
                    "description": "Shared access"
                  },
                  "show_restore": true,
                  "show_terms": true,
                  "show_privacy": true,
                  "terms_url": "https://example.com/terms",
                  "privacy_url": "https://example.com/privacy",
                  "payment_channels": ["google_play"]
                }
            """.trimIndent()
        )

        assertEquals("default_subscription_v1", pageConfig.templateId)
        assertEquals("Purchase one product and get VIP access to four other products.", pageConfig.benefitDescription)
        assertEquals(listOf("Unlimited usage", "Remove ads"), pageConfig.benefits)
        assertEquals("membership share", pageConfig.sharedApps.title)
        assertEquals(PaymentChannel.GOOGLE_PLAY, pageConfig.paymentChannels.first())
        assertEquals(true, pageConfig.showPaymentChannel)
        assertEquals(true, pageConfig.showPrivacy)
        assertEquals("https://example.com/terms", pageConfig.termsUrl)
        assertEquals("https://example.com/privacy", pageConfig.privacyUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTermsWithoutUrl() {
        SubscriptionPageConfig(showTerms = true, termsUrl = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrivacyWithoutUrl() {
        SubscriptionPageConfig(showPrivacy = true, privacyUrl = "")
    }

    @Test
    fun subscriptionPageUsesDefaultLegalLinks() {
        val config = SubscriptionPageConfig()

        assertEquals(true, config.showTerms)
        assertEquals(true, config.showPrivacy)
        assertEquals(SubscriptionPageConfig.DEFAULT_TERMS_URL, config.termsUrl)
        assertEquals(SubscriptionPageConfig.DEFAULT_PRIVACY_URL, config.privacyUrl)

        val parsed = SubscriptionPageConfigParser.parse("{}")
        assertEquals(true, parsed.showTerms)
        assertEquals(true, parsed.showPrivacy)
        assertEquals(SubscriptionPageConfig.DEFAULT_TERMS_URL, parsed.termsUrl)
        assertEquals(SubscriptionPageConfig.DEFAULT_PRIVACY_URL, parsed.privacyUrl)

        val hidden = SubscriptionPageConfigParser.parse(
            """{"showTerms":false,"showPrivacy":false,"termsUrl":"","privacyUrl":""}"""
        )
        assertEquals(false, hidden.showTerms)
        assertEquals(false, hidden.showPrivacy)

        SubscriptionPageConfig(
            showTerms = false,
            showPrivacy = false,
            termsUrl = "",
            privacyUrl = ""
        )
    }
}
