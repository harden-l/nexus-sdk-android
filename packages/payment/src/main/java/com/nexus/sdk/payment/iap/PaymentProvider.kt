package com.nexus.sdk.payment.iap

import android.app.Activity
import com.nexus.sdk.payment.config.PaymentContext
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductType

interface PaymentProvider {
    val channel: PaymentChannel
    val requiresServerVerification: Boolean

    fun getProducts(productIds: List<String>): List<Product>

    fun purchase(request: PurchaseRequest): ProviderPurchaseResult

    fun purchase(activity: Activity, request: PurchaseRequest, callback: (ProviderPurchaseResult) -> Unit) {
        callback(purchase(request))
    }

    fun restore(): RestoreResult {
        return RestoreResult(channel = channel, purchases = emptyList())
    }
}

data class PurchaseRequest(
    val product: Product,
    val uid: String,
    val paymentContext: PaymentContext = PaymentContext()
)

data class ProviderPurchaseResult(
    val channel: PaymentChannel,
    val product: Product,
    val success: Boolean,
    val orderId: String? = null,
    val purchaseToken: String? = null,
    val platformProductId: String = product.marketProductId,
    val isSubscription: Boolean = product.productType == ProductType.SUBSCRIPTION,
    val message: String? = null,
    val rawData: Map<String, Any?> = emptyMap()
)

data class RestoreResult(
    val channel: PaymentChannel,
    val purchases: List<ProviderPurchaseResult>,
    val message: String? = null
)

class MockPaymentProvider : PaymentProvider {
    override val channel: PaymentChannel = PaymentChannel.MOCK
    override val requiresServerVerification: Boolean = false

    override fun getProducts(productIds: List<String>): List<Product> = emptyList()

    override fun purchase(request: PurchaseRequest): ProviderPurchaseResult {
        val orderId = "mock_${request.product.marketProductId}_${System.currentTimeMillis()}"
        return ProviderPurchaseResult(
            channel = channel,
            product = request.product,
            success = true,
            orderId = orderId,
            purchaseToken = orderId,
            message = "Mock purchase success"
        )
    }
}

class ThirdPartyPaymentProvider(
    override val channel: PaymentChannel
) : PaymentProvider {
    init {
        require(channel in setOf(PaymentChannel.STRIPE, PaymentChannel.PAYPAL, PaymentChannel.WEB_CHECKOUT)) {
            "ThirdPartyPaymentProvider only supports third-party channels"
        }
    }

    override val requiresServerVerification: Boolean = true

    override fun getProducts(productIds: List<String>): List<Product> = emptyList()

    override fun purchase(request: PurchaseRequest): ProviderPurchaseResult {
        return ProviderPurchaseResult(
            channel = channel,
            product = request.product,
            success = false,
            message = "${channel.wireValue} payment provider is registered as a stub"
        )
    }
}
