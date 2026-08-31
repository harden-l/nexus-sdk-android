package com.nexus.sdk.growth.purchase_revenue

import com.nexus.sdk.growth.event_router.AnalyticsConfig
import com.nexus.sdk.growth.event_router.GrowthAnalyticsAdSDK
import com.nexus.sdk.growth.event_router.MockAnalyticsProvider
import com.nexus.sdk.growth.purchase_revenue.PaymentChannel
import com.nexus.sdk.growth.purchase_revenue.PurchaseRevenuePayload
import com.nexus.sdk.growth.purchase_revenue.PurchaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseRevenueTest {
    @Test
    fun reportsPurchaseRevenue() {
        val provider = MockAnalyticsProvider("bi")
        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(provider)
        )

        val event = GrowthAnalyticsAdSDK.reportPurchaseRevenue(
            PurchaseRevenuePayload(
                paymentChannel = PaymentChannel.GOOGLE_PLAY,
                orderId = "GPA.123",
                transactionId = "token_123",
                storeProductId = "vip_monthly",
                purchaseType = PurchaseType.SUBSCRIPTION,
                subscriptionPeriod = "monthly",
                currency = "USD",
                revenue = 9.99,
                country = "US"
            )
        )

        assertEquals("purchase_revenue", event?.eventName)
        assertEquals("iap", event?.params?.get("revenue_type"))
        assertEquals("google_play", event?.params?.get("payment_channel"))
        assertEquals(1, provider.snapshot().size)
    }

    @Test
    fun dedupesByTransactionId() {
        val provider = MockAnalyticsProvider("bi")
        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(provider)
        )

        val payload = PurchaseRevenuePayload(
            paymentChannel = PaymentChannel.GOOGLE_PLAY,
            orderId = "GPA.123",
            transactionId = "token_123",
            storeProductId = "vip_monthly",
            purchaseType = PurchaseType.SUBSCRIPTION,
            currency = "USD",
            revenue = 9.99
        )

        GrowthAnalyticsAdSDK.reportPurchaseRevenue(payload)
        val duplicate = GrowthAnalyticsAdSDK.reportPurchaseRevenue(payload)

        assertNull(duplicate)
        assertEquals(1, provider.snapshot().size)
    }
}
