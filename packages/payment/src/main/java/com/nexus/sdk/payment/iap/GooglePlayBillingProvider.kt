package com.nexus.sdk.payment.iap

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GooglePlayBillingProvider(
    context: Context
) : PaymentProvider, PurchasesUpdatedListener {
    override val channel: PaymentChannel = PaymentChannel.GOOGLE_PLAY
    override val requiresServerVerification: Boolean = true

    private val appContext = context.applicationContext
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private val pendingPurchases = mutableMapOf<String, PendingPurchase>()
    private var unmatchedPurchaseListener: ((ProviderPurchaseResult) -> Unit)? = null
    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    override fun getProducts(productIds: List<String>): List<Product> {
        if (productIds.isEmpty()) return emptyList()
        val products = mutableListOf<Product>()
        val latch = CountDownLatch(2)
        ensureReady(
            onReady = {
                queryProductDetails(productIds, BillingClient.ProductType.SUBS) {
                    products += it.map { details -> details.toProduct(ProductType.SUBSCRIPTION) }
                    latch.countDown()
                }
                queryProductDetails(productIds, BillingClient.ProductType.INAPP) {
                    products += it.map { details -> details.toProduct(ProductType.IAP) }
                    latch.countDown()
                }
            },
            onFailure = {
                latch.countDown()
                latch.countDown()
            }
        )
        latch.await(BILLING_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return products.distinctBy { it.marketProductId }
    }

    override fun purchase(request: PurchaseRequest): ProviderPurchaseResult {
        return ProviderPurchaseResult(
            channel = channel,
            product = request.product,
            success = false,
            message = "Google Play purchase requires an Activity. Use PaymentSDK.purchase(activity, product, ...)."
        )
    }

    override fun purchase(activity: Activity, request: PurchaseRequest, callback: (ProviderPurchaseResult) -> Unit) {
        ensureReady(
            onReady = {
                queryProductDetails(
                    product = request.product,
                    onSuccess = { details ->
                        val offerToken = offerToken(details, request.product)
                        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .apply {
                                if (!offerToken.isNullOrBlank()) setOfferToken(offerToken)
                            }
                            .build()
                        val params = BillingFlowParams.newBuilder()
                            .setObfuscatedAccountId(request.uid)
                            .setProductDetailsParamsList(listOf(productDetailsParams))
                            .build()
                        pendingPurchases[request.product.marketProductId] = PendingPurchase(request, callback)
                        val result = billingClient.launchBillingFlow(activity, params)
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                            pendingPurchases.remove(request.product.marketProductId)
                            callback(request.failure(result.debugMessage))
                        }
                    },
                    onFailure = { message -> callback(request.failure(message)) }
                )
            },
            onFailure = { message -> callback(request.failure(message)) }
        )
    }

    override fun restore(): RestoreResult {
        val restored = mutableListOf<ProviderPurchaseResult>()
        val errors = mutableListOf<String>()
        val latch = CountDownLatch(2)
        ensureReady(
            onReady = {
                queryPurchases(
                    productType = BillingClient.ProductType.INAPP,
                    onPurchase = { restored += it },
                    onComplete = { message ->
                        message?.let { errors += it }
                        latch.countDown()
                    }
                )
                queryPurchases(
                    productType = BillingClient.ProductType.SUBS,
                    onPurchase = { restored += it },
                    onComplete = { message ->
                        message?.let { errors += it }
                        latch.countDown()
                    }
                )
            },
            onFailure = { message ->
                errors += message
                latch.countDown()
                latch.countDown()
            }
        )
        val completed = latch.await(BILLING_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val message = when {
            !completed -> "Google Play restore timed out"
            errors.isNotEmpty() -> errors.joinToString("; ")
            else -> null
        }
        return RestoreResult(channel = channel, purchases = restored, message = message)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val code = billingResult.responseCode
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            failPendingPurchases("Google Play purchase cancelled")
            return
        }
        if (code != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            failPendingPurchases(billingResult.debugMessage.ifBlank { "Google Play purchase failed" })
            return
        }
        purchases.forEach { purchase ->
            val productId = purchase.products.firstOrNull()
            val pending = productId?.let { pendingPurchases[it] }
            val product = pending?.request?.product ?: purchase.toProduct()
            val result = purchase.toProviderResult(product)
            if (pending != null) {
                pending.callback(result)
                if (purchase.purchaseState != Purchase.PurchaseState.PENDING) {
                    pendingPurchases.remove(productId)
                }
            } else {
                unmatchedPurchaseListener?.invoke(result)
            }
        }
    }

    fun setUnmatchedPurchaseListener(listener: ((ProviderPurchaseResult) -> Unit)?) {
        unmatchedPurchaseListener = listener
    }

    fun acknowledgePurchase(purchaseToken: String, callback: (Boolean, String?) -> Unit = { _, _ -> }) {
        ensureReady(
            onReady = {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    callback(result.responseCode == BillingClient.BillingResponseCode.OK, result.debugMessage)
                }
            },
            onFailure = { message -> callback(false, message) }
        )
    }

    fun settlePurchase(result: ProviderPurchaseResult): BillingOperationResult {
        val token = result.purchaseToken
            ?: return BillingOperationResult(false, "Purchase token is required")
        val shouldConsume = result.product.productType == ProductType.CONSUMABLE ||
            (result.product.productType == ProductType.IAP && (result.product.coinsGranted ?: 0.0) > 0.0)
        if (!shouldConsume && result.rawData["is_acknowledged"] == true) {
            return BillingOperationResult(true, null)
        }

        val failures = mutableListOf<String>()
        repeat(BILLING_SETTLEMENT_MAX_ATTEMPTS) { attempt ->
            val operation = settlePurchaseOnce(token, shouldConsume)
            if (operation.success) return operation
            failures += "attempt ${attempt + 1}: ${operation.message ?: "unknown error"}"
            if (attempt < BILLING_SETTLEMENT_MAX_ATTEMPTS - 1) {
                Thread.sleep(BILLING_SETTLEMENT_RETRY_DELAY_MS)
            }
        }
        return BillingOperationResult(false, failures.joinToString("; "))
    }

    private fun settlePurchaseOnce(token: String, shouldConsume: Boolean): BillingOperationResult {
        val latch = CountDownLatch(1)
        var operationResult = BillingOperationResult(false, "Google Play settlement timed out")
        ensureReady(
            onReady = {
                if (shouldConsume) {
                    billingClient.consumeAsync(
                        ConsumeParams.newBuilder().setPurchaseToken(token).build()
                    ) { billingResult, _ ->
                        operationResult = BillingOperationResult(
                            billingResult.responseCode == BillingClient.BillingResponseCode.OK,
                            billingResult.debugMessage
                        )
                        latch.countDown()
                    }
                } else {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
                    ) { billingResult ->
                        operationResult = BillingOperationResult(
                            billingResult.responseCode == BillingClient.BillingResponseCode.OK,
                            billingResult.debugMessage
                        )
                        latch.countDown()
                    }
                }
            },
            onFailure = { message ->
                operationResult = BillingOperationResult(false, message)
                latch.countDown()
            }
        )
        latch.await(BILLING_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return operationResult
    }

    private fun ensureReady(onReady: () -> Unit, onFailure: (String) -> Unit) {
        if (billingClient.isReady) {
            onReady()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady()
                } else {
                    onFailure(result.debugMessage.ifBlank { "Billing setup failed" })
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProductDetails(
        product: Product,
        onSuccess: (ProductDetails) -> Unit,
        onFailure: (String) -> Unit
    ) {
        productDetailsById[product.marketProductId]?.let {
            onSuccess(it)
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.marketProductId)
                        .setProductType(product.billingProductType())
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onFailure(result.debugMessage.ifBlank { "Query product details failed" })
                return@queryProductDetailsAsync
            }
            val detail = details.productDetailsList.firstOrNull()
            if (detail == null) {
                onFailure("Product details not found: ${product.marketProductId}")
                return@queryProductDetailsAsync
            }
            productDetailsById[product.marketProductId] = detail
            onSuccess(detail)
        }
    }

    private fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        onComplete: (List<ProductDetails>) -> Unit
    ) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                }
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onComplete(emptyList())
                return@queryProductDetailsAsync
            }
            val productDetails = details.productDetailsList
            productDetails.forEach { productDetailsById[it.productId] = it }
            onComplete(productDetails)
        }
    }

    private fun queryPurchases(
        productType: String,
        onPurchase: (ProviderPurchaseResult) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onComplete(result.debugMessage.ifBlank { "Query purchases failed" })
                return@queryPurchasesAsync
            }
            purchases.forEach { purchase ->
                val productId = purchase.products.firstOrNull().orEmpty()
                onPurchase(
                    purchase.toProviderResult(
                        Product(
                            marketProductId = productId,
                            name = productId,
                            description = "",
                            productType = if (productType == BillingClient.ProductType.SUBS) {
                                ProductType.SUBSCRIPTION
                            } else {
                                ProductType.IAP
                            }
                        )
                    )
                )
            }
            onComplete(null)
        }
    }

    private fun offerToken(details: ProductDetails, product: Product): String? {
        if (product.productType != ProductType.SUBSCRIPTION) return null
        return details.subscriptionOfferDetails?.firstOrNull()?.offerToken
    }

    private fun Product.billingProductType(): String {
        return if (productType == ProductType.SUBSCRIPTION) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
    }

    private fun Purchase.toProviderResult(product: Product): ProviderPurchaseResult {
        val orderId = orderId ?: purchaseToken
        return ProviderPurchaseResult(
            channel = channel,
            product = product,
            success = purchaseState == Purchase.PurchaseState.PURCHASED,
            orderId = orderId,
            purchaseToken = purchaseToken,
            platformProductId = product.marketProductId,
            isSubscription = product.productType == ProductType.SUBSCRIPTION,
            message = if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                "Google Play purchase success"
            } else {
                "Google Play purchase is pending"
            },
            rawData = mapOf(
                "signature" to signature,
                "is_acknowledged" to isAcknowledged
            )
        )
    }

    private fun Purchase.toProduct(): Product {
        val productId = products.firstOrNull().orEmpty()
        val details = productDetailsById[productId]
        return details?.toProduct(
            if (details.subscriptionOfferDetails != null) ProductType.SUBSCRIPTION else ProductType.IAP
        ) ?: Product(
            marketProductId = productId,
            name = productId,
            description = "",
            productType = ProductType.UNKNOWN
        )
    }

    private fun ProductDetails.toProduct(productType: ProductType): Product {
        val pricing = if (productType == ProductType.SUBSCRIPTION) {
            subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
        } else {
            null
        }
        val oneTime = oneTimePurchaseOfferDetails
        return Product(
            marketProductId = productId,
            name = name,
            description = description,
            productType = productType,
            price = when {
                pricing != null -> (pricing.priceAmountMicros / 1_000_000.0).toString()
                oneTime != null -> (oneTime.priceAmountMicros / 1_000_000.0).toString()
                else -> null
            },
            currency = pricing?.priceCurrencyCode ?: oneTime?.priceCurrencyCode,
            localizedPrice = pricing?.formattedPrice ?: oneTime?.formattedPrice,
            subscriptionPeriod = pricing?.billingPeriod
        )
    }

    private fun PurchaseRequest.failure(message: String): ProviderPurchaseResult {
        return ProviderPurchaseResult(
            channel = channel,
            product = product,
            success = false,
            message = message
        )
    }

    private fun failPendingPurchases(message: String) {
        val pending = pendingPurchases.values.toList()
        pendingPurchases.clear()
        pending.forEach { it.callback(it.request.failure(message)) }
    }

    private data class PendingPurchase(
        val request: PurchaseRequest,
        val callback: (ProviderPurchaseResult) -> Unit
    )

    data class BillingOperationResult(
        val success: Boolean,
        val message: String?
    )

    private companion object {
        private const val BILLING_QUERY_TIMEOUT_SECONDS = 8L
        private const val BILLING_SETTLEMENT_MAX_ATTEMPTS = 3
        private const val BILLING_SETTLEMENT_RETRY_DELAY_MS = 500L
    }
}
