package com.nexus.sdk.payment.products

import com.nexus.sdk.payment.products.ProductType
import com.nexus.sdk.payment.products.ProductParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductParserTest {
    @Test
    fun parsesProductList() {
        val response = """
            {
              "code": 1,
              "message": "success",
              "data": {
                "list": [
                  {
                    "market_product_id": "coins_100",
                    "name": "100金币",
                    "description": "购买获得100金币",
                    "product_type": 1,
                    "coins_granted": 100.25,
                    "price": "1.99",
                    "currency": "USD",
                    "localized_price": "$1.99",
                    "subscription_period": "monthly",
                    "trial_period": "7d",
                    "has_trial": true,
                    "entitlement_id": "vip",
                    "benefits": ["no_ads", "premium"],
                    "weekly_points_enabled": 1,
                    "weekly_points": 30
                  }
                ]
              }
            }
        """.trimIndent()

        val products = ProductParser.parseProducts(response)

        assertEquals(1, products.size)
        assertEquals("coins_100", products.first().marketProductId)
        assertEquals(ProductType.CONSUMABLE, products.first().productType)
        assertEquals(100.25, products.first().coinsGranted ?: 0.0, 0.0)
        assertEquals("1.99", products.first().price)
        assertEquals("USD", products.first().currency)
        assertEquals("$1.99", products.first().localizedPrice)
        assertEquals("monthly", products.first().subscriptionPeriod)
        assertEquals(true, products.first().hasTrial)
        assertEquals(listOf("no_ads", "premium"), products.first().benefits)
        assertEquals(true, products.first().weeklyPointsEnabled)
        assertEquals(30, products.first().weeklyPoints)
    }

}
