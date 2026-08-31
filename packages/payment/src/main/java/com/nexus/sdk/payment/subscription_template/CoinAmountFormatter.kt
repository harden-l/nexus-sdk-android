package com.nexus.sdk.payment.subscription_template

import java.math.BigDecimal

internal object CoinAmountFormatter {
    private val displayScale = BigDecimal.valueOf(100)

    fun displayText(value: Double): String {
        return BigDecimal.valueOf(value)
            .multiply(displayScale)
            .stripTrailingZeros()
            .toPlainString()
    }
}
