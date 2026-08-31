package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.config.PaymentChannel

data class SubscriptionPageConfig(
    val templateId: String = SubscriptionPageTemplates.AURORA,
    val scene: String = "",
    val title: String = "Upgrade to Pro",
    val benefitDescription: String = "Purchase one product and get VIP access to all other products.",
    val benefits: List<String> = emptyList(),
    val sharedApps: SubscriptionSharedAppsConfig = SubscriptionSharedAppsConfig(),
    val paymentChannels: List<PaymentChannel> = emptyList(),
    val showPaymentChannel: Boolean = true,
    val showRestore: Boolean = true,
    val showTerms: Boolean = true,
    val showPrivacy: Boolean = true,
    val termsUrl: String = DEFAULT_TERMS_URL,
    val privacyUrl: String = DEFAULT_PRIVACY_URL,
    val ctaText: String = "Start Pro",
    val restoreText: String = "Restore Purchase",
    val termsText: String = "Terms",
    val privacyText: String = "Privacy"
) {
    companion object {
        const val DEFAULT_TERMS_URL = "https://www.crypsiscollectiveinc.com/terms.html"
        const val DEFAULT_PRIVACY_URL = "https://www.crypsiscollectiveinc.com/privacy.html"
    }

    init {
        require(!showTerms || termsUrl.isNotBlank()) {
            "termsUrl is required when showTerms is true"
        }
        require(!showPrivacy || privacyUrl.isNotBlank()) {
            "privacyUrl is required when showPrivacy is true"
        }
    }
}

data class SubscriptionSharedAppsConfig(
    val title: String = "membership share",
    val description: String = "Your membership gives you access to every current service in this app."
)

data class SubscriptionSharedAppItem(
    val title: String,
    val description: String,
    val icon: String = ""
)

enum class SubscriptionPageEventName(val wireValue: String) {
    PAGE_SHOW("purchase_page_show"),
    PRODUCT_SELECT("purchase_product_select"),
    CHANNEL_SELECT("purchase_channel_select"),
    PURCHASE_CLICK("purchase_click"),
    PURCHASE_SUCCESS("purchase_success"),
    PURCHASE_FAILED("purchase_failed"),
    PURCHASE_CANCEL("purchase_cancel"),
    WEEKLY_POINTS_CLAIM_CLICK("weekly_points_claim_click"),
    WEEKLY_POINTS_CLAIM_SUCCESS("weekly_points_claim_success"),
    WEEKLY_POINTS_CLAIM_FAILED("weekly_points_claim_failed"),
    RESTORE_CLICK("purchase_restore_click"),
    RESTORE_SUCCESS("purchase_restore_success"),
    RESTORE_FAILED("purchase_restore_failed"),
    CLOSE("purchase_page_close")
}

enum class SubscriptionPageState {
    LOADING,
    READY,
    PURCHASING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class SubscriptionPageEvent(
    val name: SubscriptionPageEventName,
    val productId: String? = null,
    val paymentChannel: PaymentChannel? = null,
    val state: SubscriptionPageState? = null,
    val params: Map<String, Any?> = emptyMap()
) {
    fun analyticsName(): String = name.wireValue

    fun analyticsParams(): Map<String, Any?> {
        return buildMap {
            productId?.let { put("product_id", it) }
            paymentChannel?.let { put("payment_channel", it.wireValue) }
            state?.let { put("state", it.name.lowercase()) }
            putAll(params)
        }
    }
}
