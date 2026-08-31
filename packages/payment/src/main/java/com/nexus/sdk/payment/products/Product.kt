package com.nexus.sdk.payment.products

data class Product(
    val marketProductId: String,
    val name: String,
    val description: String,
    val productType: ProductType,
    val coinsGranted: Double? = null,
    val price: String? = null,
    val currency: String? = null,
    val localizedPrice: String? = null,
    val subscriptionPeriod: String? = null,
    val trialPeriod: String? = null,
    val hasTrial: Boolean = false,
    val entitlementId: String? = null,
    val benefits: List<String> = emptyList(),
    val weeklyPointsEnabled: Boolean = false,
    val weeklyPoints: Int = 0
)
