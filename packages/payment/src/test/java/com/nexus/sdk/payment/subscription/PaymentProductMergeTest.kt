package com.nexus.sdk.payment.subscription

import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductDetailsMerger
import com.nexus.sdk.payment.products.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentProductMergeTest {
    @Test
    fun keepsApiProductsThatAreMissingFromGooglePlay() {
        val apiProducts = listOf(
            apiProduct("subscription", ProductType.SUBSCRIPTION),
            apiProduct("credits", ProductType.CONSUMABLE)
        )

        val merged = ProductDetailsMerger.merge(apiProducts, emptyList())

        assertEquals(listOf("subscription", "credits"), merged.map { it.marketProductId })
        assertNull(merged.first().localizedPrice)
    }

    @Test
    fun enrichesMatchingProductsWithoutDroppingUnmatchedApiProducts() {
        val apiProducts = listOf(
            apiProduct("subscription", ProductType.UNKNOWN),
            apiProduct("credits", ProductType.CONSUMABLE)
        )
        val billingProducts = listOf(
            Product(
                marketProductId = "subscription",
                name = "Premium Monthly",
                description = "Google Play description",
                productType = ProductType.SUBSCRIPTION,
                localizedPrice = "$4.99",
                currency = "USD",
                subscriptionPeriod = "P1M"
            )
        )

        val merged = ProductDetailsMerger.merge(apiProducts, billingProducts)

        assertEquals(2, merged.size)
        assertEquals("Premium Monthly", merged[0].name)
        assertEquals("API description", merged[0].description)
        assertEquals(ProductType.SUBSCRIPTION, merged[0].productType)
        assertEquals("$4.99", merged[0].localizedPrice)
        assertEquals("P1M", merged[0].subscriptionPeriod)
        assertEquals(20.0, merged[0].coinsGranted ?: 0.0, 0.0)
        assertEquals("credits", merged[1].marketProductId)
        assertNull(merged[1].localizedPrice)
    }

    private fun apiProduct(id: String, type: ProductType): Product {
        return Product(
            marketProductId = id,
            name = "",
            description = "API description",
            productType = type,
            coinsGranted = 20.0
        )
    }
}
