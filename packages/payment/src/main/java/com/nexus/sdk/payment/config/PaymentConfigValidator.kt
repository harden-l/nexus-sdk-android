package com.nexus.sdk.payment.config

import com.nexus.sdk.payment.config.PaymentChannel
internal object PaymentConfigValidator {
    fun validate(config: PaymentConfig) {
        require(config.enabledChannels.distinct().size == config.enabledChannels.size) {
            "enabledChannels contains duplicates"
        }
        require(config.fallbackChannels.all { it in config.enabledChannels }) {
            "fallbackChannels must be included in enabledChannels"
        }
        config.rules.forEachIndexed { index, rule ->
            require(rule.enabledChannels.distinct().size == rule.enabledChannels.size) {
                "rules[$index].enabledChannels contains duplicates"
            }
            require(rule.fallbackChannels.all { it in rule.enabledChannels }) {
                "rules[$index].fallbackChannels must be included in enabledChannels"
            }
        }
    }
}