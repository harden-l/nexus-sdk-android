package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.subscription_template.SubscriptionPageEvent
import com.nexus.sdk.payment.subscription_template.SubscriptionPageEventName
import com.nexus.sdk.payment.subscription_template.SubscriptionPageState
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionPageEventTest {
    @Test
    fun formatsApiCoinAmountUsingSubscriptionDisplayScale() {
        assertEquals("2000", CoinAmountFormatter.displayText(20.0))
        assertEquals("2025", CoinAmountFormatter.displayText(20.25))
        assertEquals("12.5", CoinAmountFormatter.displayText(0.125))
    }

    @Test
    fun buildsAnalyticsPayload() {
        val event = SubscriptionPageEvent(
            name = SubscriptionPageEventName.PURCHASE_CLICK,
            productId = "premium_monthly",
            paymentChannel = PaymentChannel.GOOGLE_PLAY,
            state = SubscriptionPageState.PURCHASING,
            params = mapOf("entry" to "settings")
        )

        assertEquals("purchase_click", event.analyticsName())
        assertEquals("premium_monthly", event.analyticsParams()["product_id"])
        assertEquals("google_play", event.analyticsParams()["payment_channel"])
        assertEquals("purchasing", event.analyticsParams()["state"])
        assertEquals("settings", event.analyticsParams()["entry"])
    }
}
