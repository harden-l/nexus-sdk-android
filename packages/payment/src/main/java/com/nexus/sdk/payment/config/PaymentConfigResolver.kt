package com.nexus.sdk.payment.config

internal object PaymentConfigResolver {
    fun resolve(config: PaymentConfig, context: PaymentContext): ResolvedPaymentChannels {
        val matchedRule = config.rules.firstOrNull { rule -> rule.matches(context) }
        return if (matchedRule != null) {
            ResolvedPaymentChannels(
                defaultChannel = matchedRule.defaultChannel,
                enabledChannels = matchedRule.enabledChannels,
                fallbackChannels = matchedRule.fallbackChannels,
                matchedRule = matchedRule
            )
        } else {
            ResolvedPaymentChannels(
                defaultChannel = config.defaultChannel,
                enabledChannels = config.enabledChannels,
                fallbackChannels = config.fallbackChannels
            )
        }
    }

    private fun PaymentRule.matches(context: PaymentContext): Boolean {
        if (!country.isNullOrBlank() && !country.equals(context.country, ignoreCase = true)) return false
        if (!platform.isNullOrBlank() && !platform.equals(context.platform, ignoreCase = true)) return false
        if (!minVersion.isNullOrBlank() && !context.appVersion.isNullOrBlank()) {
            if (compareVersions(context.appVersion, minVersion) < 0) return false
        }
        return true
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until max) {
            val diff = leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
