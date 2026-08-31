package com.nexus.sdk.payment.products

import com.nexus.sdk.payment.config.SimpleJson
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductType

internal object ProductParser {
    fun parseProducts(response: String): List<Product> {
        return SimpleJson.findObjectsInList(response, "list").map { objectJson ->
            val coinsGranted = SimpleJson.findDouble(objectJson, "coins_granted")
            Product(
                marketProductId = SimpleJson.findString(objectJson, "market_product_id").orEmpty(),
                name = SimpleJson.findString(objectJson, "name").orEmpty(),
                description = SimpleJson.findString(objectJson, "description").orEmpty(),
                productType = parseType(SimpleJson.findInt(objectJson, "product_type"), coinsGranted),
                coinsGranted = coinsGranted,
                price = SimpleJson.findString(objectJson, "price"),
                currency = SimpleJson.findString(objectJson, "currency"),
                localizedPrice = SimpleJson.findString(objectJson, "localized_price"),
                subscriptionPeriod = SimpleJson.findString(objectJson, "subscription_period"),
                trialPeriod = SimpleJson.findString(objectJson, "trial_period"),
                hasTrial = SimpleJson.findBoolean(objectJson, "has_trial") ?: false,
                entitlementId = SimpleJson.findString(objectJson, "entitlement_id"),
                benefits = SimpleJson.findStringArray(objectJson, "benefits"),
                weeklyPointsEnabled = (SimpleJson.findInt(objectJson, "weekly_points_enabled") ?: 0) == 1,
                weeklyPoints = SimpleJson.findInt(objectJson, "weekly_points") ?: 0
            )
        }.filter { it.marketProductId.isNotBlank() }
    }

    private fun parseType(value: Int?, coinsGranted: Double?): ProductType {
        return when (value) {
            1 -> if ((coinsGranted ?: 0.0) > 0.0) ProductType.CONSUMABLE else ProductType.IAP
            2 -> ProductType.SUBSCRIPTION
            else -> ProductType.UNKNOWN
        }
    }

}
