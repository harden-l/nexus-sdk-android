package com.nexus.sdk.payment.iap

import com.nexus.sdk.payment.entitlement.Entitlement
import com.nexus.sdk.payment.order_verify.OrderVerificationResult
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product

data class PurchaseResult(
    val channel: PaymentChannel,
    val product: Product,
    val success: Boolean,
    val orderId: String? = null,
    val message: String? = null,
    val verification: OrderVerificationResult? = null,
    val entitlement: Entitlement? = null
)
