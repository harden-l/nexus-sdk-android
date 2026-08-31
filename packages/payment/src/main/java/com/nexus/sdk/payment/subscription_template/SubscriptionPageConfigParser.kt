package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.config.SimpleJson
import com.nexus.sdk.payment.config.PaymentChannel

internal object SubscriptionPageConfigParser {
    fun parse(raw: String): SubscriptionPageConfig {
        val sharedAppsRaw = SimpleJson.findObject(raw, "shared_apps")
            ?: SimpleJson.findObject(raw, "sharedApps")
            ?: SimpleJson.findObject(raw, "benefits")
        val paymentChannels =
            SimpleJson.findStringArray(raw, "payment_channels").mapNotNull(::paymentChannel)
        return SubscriptionPageConfig(
            templateId = SimpleJson.findString(raw, "template_id")
                ?: SubscriptionPageTemplates.AURORA,
            scene = SimpleJson.findString(raw, "scene").orEmpty(),
            title = SimpleJson.findString(raw, "title").orEmpty(),
            benefitDescription = SimpleJson.findString(raw, "benefit_description")
                ?: SimpleJson.findString(raw, "benefitDescription")
                ?: SimpleJson.findString(raw, "entitlement_description")
                ?: SimpleJson.findString(raw, "entitlementDescription")
                ?: SimpleJson.findString(raw, "subTitle")
                ?: "",
            benefits = SimpleJson.findStringArray(raw, "benefits"),
            sharedApps = parseSharedApps(sharedAppsRaw),
            paymentChannels = paymentChannels,
            showPaymentChannel = SimpleJson.findBoolean(raw, "show_payment_channel")
                ?: paymentChannels.isNotEmpty(),
            showRestore = SimpleJson.findBoolean(raw, "show_restore") ?: false,
            showTerms = SimpleJson.findBoolean(raw, "show_terms")
                ?: SimpleJson.findBoolean(raw, "showTerms")
                ?: true,
            showPrivacy = SimpleJson.findBoolean(raw, "show_privacy")
                ?: SimpleJson.findBoolean(raw, "showPrivacy")
                ?: true,
            termsUrl = SimpleJson.findString(raw, "terms_url")
                ?: SimpleJson.findString(raw, "termsUrl")
                ?: SubscriptionPageConfig.DEFAULT_TERMS_URL,
            privacyUrl = SimpleJson.findString(raw, "privacy_url")
                ?: SimpleJson.findString(raw, "privacyUrl")
                ?: SubscriptionPageConfig.DEFAULT_PRIVACY_URL,
            ctaText = SimpleJson.findString(raw, "cta_text") ?: "Start Pro",
            restoreText = SimpleJson.findString(raw, "restore_text") ?: "Restore Purchase",
            termsText = SimpleJson.findString(raw, "terms_text") ?: "Terms",
            privacyText = SimpleJson.findString(raw, "privacy_text") ?: "Privacy"
        )
    }

    private fun parseSharedApps(raw: String?): SubscriptionSharedAppsConfig {
        if (raw.isNullOrBlank()) return SubscriptionSharedAppsConfig()
        return SubscriptionSharedAppsConfig(
            title = SimpleJson.findString(raw, "title").orEmpty(),
            description = SimpleJson.findString(raw, "description").orEmpty()
        )
    }

    private fun paymentChannel(value: String): PaymentChannel? {
        return PaymentChannel.entries.firstOrNull { it.wireValue == value }
    }
}
