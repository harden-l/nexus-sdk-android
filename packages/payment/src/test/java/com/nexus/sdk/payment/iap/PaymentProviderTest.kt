package com.nexus.sdk.payment.iap

import com.nexus.sdk.payment.entitlement.EntitlementStore
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductType
import com.nexus.sdk.payment.order_verify.OrderStatus
import com.nexus.sdk.payment.order_verify.OrderVerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentProviderTest {
    @Test
    fun mockProviderReturnsSuccessfulPurchase() {
        val product = product()
        val result = MockPaymentProvider().purchase(PurchaseRequest(product = product, uid = "uid_1"))

        assertTrue(result.success)
        assertEquals(PaymentChannel.MOCK, result.channel)
        assertEquals(product.marketProductId, result.platformProductId)
        assertFalse(result.isSubscription)
    }

    @Test
    fun thirdPartyProviderIsRegisteredAsDisabledStub() {
        val result = ThirdPartyPaymentProvider(PaymentChannel.STRIPE)
            .purchase(PurchaseRequest(product = product(), uid = "uid_1"))

        assertFalse(result.success)
        assertEquals(PaymentChannel.STRIPE, result.channel)
    }

    @Test
    fun grantsEntitlementOnlyOncePerOrder() {
        val store = EntitlementStore()
        val product = product(entitlementId = "premium")
        val verification = OrderVerificationResult(
            tradeOrderId = "order_1",
            status = OrderStatus.SUCCESS,
            isSubscription = false
        )

        val first = store.grant(product, "order_1", PaymentChannel.MOCK, verification)
        val duplicate = store.grant(product, "order_1", PaymentChannel.MOCK, verification)

        assertEquals("premium", first?.entitlementId)
        assertNull(duplicate)
        assertTrue(store.hasDelivered("order_1"))
        assertEquals(1, store.getEntitlements().size)
    }

    @Test
    fun entitlementsAndDeliveryIdsAreScopedByUser() {
        val store = EntitlementStore()
        val product = product(entitlementId = "premium")
        val verification = OrderVerificationResult(
            tradeOrderId = "subscription_original_order",
            status = OrderStatus.SUCCESS,
            isSubscription = true
        )

        val firstRenewal = store.grant(
            product,
            "subscription_original_order",
            PaymentChannel.MOCK,
            verification,
            uid = "uid_a",
            deliveryId = "transaction_1"
        )
        val secondRenewal = store.grant(
            product,
            "subscription_original_order",
            PaymentChannel.MOCK,
            verification,
            uid = "uid_a",
            deliveryId = "transaction_2"
        )

        assertEquals("premium", firstRenewal?.entitlementId)
        assertEquals("premium", secondRenewal?.entitlementId)
        assertTrue(store.hasDelivered("transaction_1", "uid_a"))
        assertTrue(store.hasDelivered("transaction_2", "uid_a"))
        assertEquals(1, store.getEntitlements("uid_a").size)
        assertFalse(store.hasDelivered("transaction_1", "uid_b"))
        assertTrue(store.getEntitlements("uid_b").isEmpty())
    }

    @Test
    fun parsesOrderStatus() {
        assertEquals(OrderStatus.SUCCESS, OrderStatus.fromCode(20))
        assertEquals(OrderStatus.FAILED, OrderStatus.fromCode(60))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromCode(999))
    }

    private fun product(entitlementId: String? = null): Product {
        return Product(
            marketProductId = "premium_monthly",
            name = "Premium",
            description = "Premium access",
            productType = ProductType.IAP,
            price = "1.99",
            currency = "USD",
            entitlementId = entitlementId
        )
    }
}
