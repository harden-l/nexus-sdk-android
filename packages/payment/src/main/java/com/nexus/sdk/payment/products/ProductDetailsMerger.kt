package com.nexus.sdk.payment.products

internal object ProductDetailsMerger {
    fun merge(apiProducts: List<Product>, billingProducts: List<Product>): List<Product> {
        val billingById = billingProducts.associateBy { it.marketProductId }
        return apiProducts.map { apiProduct ->
            val billingProduct = billingById[apiProduct.marketProductId] ?: return@map apiProduct
            apiProduct.copy(
                name = billingProduct.name.ifBlank {
                    apiProduct.name.ifBlank { apiProduct.marketProductId }
                },
                description = apiProduct.description.ifBlank { billingProduct.description },
                productType = if (apiProduct.productType == ProductType.UNKNOWN) {
                    billingProduct.productType
                } else {
                    apiProduct.productType
                },
                price = billingProduct.price ?: apiProduct.price,
                currency = billingProduct.currency ?: apiProduct.currency,
                localizedPrice = billingProduct.localizedPrice ?: apiProduct.localizedPrice,
                subscriptionPeriod = billingProduct.subscriptionPeriod ?: apiProduct.subscriptionPeriod,
                trialPeriod = billingProduct.trialPeriod ?: apiProduct.trialPeriod,
                hasTrial = apiProduct.hasTrial || billingProduct.hasTrial
            )
        }
    }
}
