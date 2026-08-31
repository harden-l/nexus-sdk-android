package com.nexus.sdk.payment.config

import com.nexus.sdk.payment.config.PaymentChannel

data class PaymentConfig(
    val productId: String,
    val platform: String = "android",
    val country: String? = null,
    val appVersion: String? = null,
    val defaultChannel: PaymentChannel = PaymentChannel.GOOGLE_PLAY,
    val enabledChannels: List<PaymentChannel> = listOf(PaymentChannel.GOOGLE_PLAY),
    val fallbackChannels: List<PaymentChannel> = emptyList(),
    val rules: List<PaymentRule> = emptyList()
) {
    init {
        require(productId.isNotBlank()) { "productId is required" }
        require(platform.isNotBlank()) { "platform is required" }
        require(enabledChannels.isNotEmpty()) { "enabledChannels is required" }
        require(defaultChannel in enabledChannels) { "defaultChannel must be included in enabledChannels" }
        PaymentConfigValidator.validate(this)
    }
}

data class PaymentRule(
    val country: String? = null,
    val platform: String? = null,
    val minVersion: String? = null,
    val enabledChannels: List<PaymentChannel>,
    val defaultChannel: PaymentChannel,
    val fallbackChannels: List<PaymentChannel> = emptyList()
) {
    init {
        require(enabledChannels.isNotEmpty()) { "rule enabledChannels is required" }
        require(defaultChannel in enabledChannels) { "rule defaultChannel must be included in enabledChannels" }
    }
}

data class PaymentContext(
    val country: String? = null,
    val platform: String = "android",
    val appVersion: String? = null
)

data class ResolvedPaymentChannels(
    val defaultChannel: PaymentChannel,
    val enabledChannels: List<PaymentChannel>,
    val fallbackChannels: List<PaymentChannel> = emptyList(),
    val matchedRule: PaymentRule? = null
)
