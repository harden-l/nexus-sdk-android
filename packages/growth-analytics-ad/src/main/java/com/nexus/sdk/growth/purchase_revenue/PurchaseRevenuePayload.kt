package com.nexus.sdk.growth.purchase_revenue

data class PurchaseRevenuePayload(
    val paymentChannel: PaymentChannel,
    val orderId: String,
    val transactionId: String? = null,
    val storeProductId: String,
    val purchaseType: PurchaseType,
    val subscriptionPeriod: String? = null,
    val currency: String,
    val revenue: Double,
    val country: String? = null,
    val isTrial: Boolean = false,
    val isRenewal: Boolean = false
) {
    init {
        require(orderId.isNotBlank()) { "orderId is required" }
        require(storeProductId.isNotBlank()) { "storeProductId is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(revenue >= 0.0) { "revenue must not be negative" }
    }

    fun dedupeKey(): String = transactionId?.takeIf { it.isNotBlank() } ?: orderId
}
