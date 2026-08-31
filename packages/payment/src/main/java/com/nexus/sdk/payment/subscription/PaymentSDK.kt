package com.nexus.sdk.payment.subscription

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nexus.sdk.coreuser.init.CoreUserSDK
import com.nexus.sdk.coreuser.network.RelatedProduct
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsClaimResult
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsInfo
import com.nexus.sdk.growth.event_router.GrowthAnalyticsAdSDK
import com.nexus.sdk.growth.purchase_revenue.PaymentChannel as GrowthPaymentChannel
import com.nexus.sdk.growth.purchase_revenue.PurchaseRevenuePayload
import com.nexus.sdk.growth.purchase_revenue.PurchaseType
import com.nexus.sdk.payment.config.PaymentConfig
import com.nexus.sdk.payment.config.PaymentConfigResolver
import com.nexus.sdk.payment.config.PaymentConfigValidator
import com.nexus.sdk.payment.config.PaymentContext
import com.nexus.sdk.payment.config.ResolvedPaymentChannels
import com.nexus.sdk.payment.entitlement.Entitlement
import com.nexus.sdk.payment.entitlement.EntitlementStore
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductDetailsMerger
import com.nexus.sdk.payment.products.ProductType
import com.nexus.sdk.payment.iap.PurchaseResult
import com.nexus.sdk.payment.products.ProductApi
import com.nexus.sdk.payment.order_verify.OrderStatus
import com.nexus.sdk.payment.order_verify.OrderVerificationApi
import com.nexus.sdk.payment.order_verify.OrderVerificationRequest
import com.nexus.sdk.payment.order_verify.OrderVerificationResult
import com.nexus.sdk.payment.iap.GooglePlayBillingProvider
import com.nexus.sdk.payment.iap.PaymentProvider
import com.nexus.sdk.payment.iap.MockPaymentProvider
import com.nexus.sdk.payment.iap.ProviderPurchaseResult
import com.nexus.sdk.payment.iap.PurchaseRequest
import com.nexus.sdk.payment.iap.RestoreResult
import com.nexus.sdk.payment.iap.ThirdPartyPaymentProvider
import com.nexus.sdk.payment.subscription_template.SubscriptionPageActivity
import com.nexus.sdk.payment.subscription_template.SubscriptionPageConfig
import com.nexus.sdk.payment.subscription_template.SubscriptionPageEvent
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

object PaymentSDK {
    const val VERSION = "0.0.12"
    private const val TAG = "PaymentSDK"

    private var config: PaymentConfig? = null
    private var productApi: ProductApi? = null
    private var orderVerificationApi: OrderVerificationApi? = null
    private var entitlementStore = EntitlementStore()
    private val providers = linkedMapOf<PaymentChannel, PaymentProvider>()
    private var googlePlayBillingProvider: GooglePlayBillingProvider? = null
    private var apiProducts: List<Product> = emptyList()
    private var products: List<Product> = emptyList()
    private var relatedProducts: List<RelatedProduct> = emptyList()
    private var activeSubscriptionPageConfig: SubscriptionPageConfig? = null
    private var subscriptionPageActivity: WeakReference<Activity>? = null
    private val subscriptionPageCallbacks = mutableSetOf<(SubscriptionPageEvent) -> Unit>()
    private val paymentExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(config: PaymentConfig) {
        val coreConfig = CoreUserSDK.getSdkConfig()
        this.config = config
        this.productApi = ProductApi(coreConfig)
        this.orderVerificationApi = OrderVerificationApi(coreConfig)
        this.entitlementStore = EntitlementStore(
            CoreUserSDK.getApplicationContext(),
            coreConfig.productId
        )
        this.apiProducts = emptyList()
        this.products = emptyList()
        this.relatedProducts = emptyList()
        this.providers.clear()
        registerProvider(MockPaymentProvider())
        this.googlePlayBillingProvider = GooglePlayBillingProvider(CoreUserSDK.getApplicationContext())
        val googleProvider = requireNotNull(googlePlayBillingProvider)
        googleProvider.setUnmatchedPurchaseListener(::processRecoveredGooglePlayPurchase)
        registerProvider(googleProvider)
        registerProvider(ThirdPartyPaymentProvider(PaymentChannel.STRIPE))
        registerProvider(ThirdPartyPaymentProvider(PaymentChannel.PAYPAL))
        registerProvider(ThirdPartyPaymentProvider(PaymentChannel.WEB_CHECKOUT))
        if (PaymentChannel.GOOGLE_PLAY in config.enabledChannels) {
            reconcileGooglePlayPurchases()
        }
    }

    fun getAvailableChannels(): List<PaymentChannel> {
        return requireConfig().enabledChannels
    }

    fun getConfig(): PaymentConfig {
        return requireConfig()
    }

    fun resolvePaymentChannel(context: PaymentContext): ResolvedPaymentChannels {
        return PaymentConfigResolver.resolve(requireConfig(), context)
    }

    fun getProducts(forceRefresh: Boolean = false): List<Product> {
        if (products.isNotEmpty() && !forceRefresh) return products
        val loadedApiProducts = getApiProducts(forceRefresh)
        products = enrichProductsWithBilling(loadedApiProducts)
        return products
    }

    internal fun getApiProducts(forceRefresh: Boolean = false): List<Product> {
        if (apiProducts.isNotEmpty() && !forceRefresh) return apiProducts
        apiProducts = requireProductApi().getProducts()
        products = apiProducts
        return apiProducts
    }

    internal fun enrichProductsWithBilling(apiProducts: List<Product>): List<Product> {
        products = mergeBillingProducts(apiProducts)
        return products
    }

    fun getRelatedProducts(forceRefresh: Boolean = false): List<RelatedProduct> {
        if (relatedProducts.isNotEmpty() && !forceRefresh) return relatedProducts
        relatedProducts = CoreUserSDK.getRelatedProducts()
        Log.d(
            TAG,
            "subscription related products loaded count=${relatedProducts.size}, " +
                "package=${CoreUserSDK.getApplicationContext().packageName}, " +
                "ids=${relatedProducts.joinToString { it.productId }}"
        )
        return relatedProducts
    }

    fun mockPurchase(product: Product): PurchaseResult {
        return purchase(product, PaymentChannel.MOCK)
    }

    fun validatePaymentConfig(config: PaymentConfig) {
        PaymentConfigValidator.validate(config)
    }

    fun registerProvider(provider: PaymentProvider) {
        providers[provider.channel] = provider
    }

    fun getRegisteredProviders(): List<PaymentProvider> = providers.values.toList()

    fun purchase(
        product: Product,
        channel: PaymentChannel? = null,
        paymentContext: PaymentContext = PaymentContext()
    ): PurchaseResult {
        val selectedChannel = channel ?: resolvePaymentChannel(paymentContext).defaultChannel
        val provider = providers[selectedChannel]
            ?: throw PaymentException("Payment provider is not registered for ${selectedChannel.wireValue}")
        if (selectedChannel !in getAvailableChannels() && selectedChannel != PaymentChannel.MOCK) {
            throw PaymentException("Payment channel is disabled: ${selectedChannel.wireValue}")
        }

        val uid = CoreUserSDK.getCurrentUser()?.uid ?: CoreUserSDK.silentLogin().uid
        val providerResult = provider.purchase(PurchaseRequest(product = product, uid = uid, paymentContext = paymentContext))
        if (!providerResult.success) {
            return PurchaseResult(
                channel = providerResult.channel,
                product = product,
                success = false,
                orderId = providerResult.orderId,
                message = providerResult.message
            )
        }

        val orderId = providerResult.orderId
            ?: throw PaymentException("Purchase succeeded without order id")
        val verification = if (provider.requiresServerVerification) {
            verifyProviderPurchase(providerResult, uid)
        } else {
            OrderVerificationResult(
                tradeOrderId = orderId,
                status = OrderStatus.SUCCESS,
                isSubscription = providerResult.isSubscription,
                token = providerResult.purchaseToken,
                platformProductId = providerResult.platformProductId
            )
        }

        if (!verification.isSuccessful) {
            return PurchaseResult(
                channel = providerResult.channel,
                product = product,
                success = false,
                orderId = orderId,
                message = "Order verification status: ${verification.status}",
                verification = verification
            )
        }

        val entitlement = entitlementStore.grant(
            product,
            orderId,
            providerResult.channel,
            verification,
            uid = uid
        )
        if (entitlement != null) {
            reportPurchaseRevenue(product, orderId, providerResult.channel, verification)
        }
        return PurchaseResult(
            channel = providerResult.channel,
            product = product,
            success = true,
            orderId = orderId,
            message = providerResult.message,
            verification = verification,
            entitlement = entitlement
        )
    }

    fun purchase(
        activity: Activity,
        product: Product,
        channel: PaymentChannel? = null,
        paymentContext: PaymentContext = PaymentContext(),
        callback: (PurchaseResult) -> Unit
    ) {
        val selectedChannel = channel ?: resolvePaymentChannel(paymentContext).defaultChannel
        val provider = providers[selectedChannel]
            ?: throw PaymentException("Payment provider is not registered for ${selectedChannel.wireValue}")
        if (selectedChannel !in getAvailableChannels() && selectedChannel != PaymentChannel.MOCK) {
            throw PaymentException("Payment channel is disabled: ${selectedChannel.wireValue}")
        }
        paymentExecutor.execute {
            val uidResult = runCatching {
                CoreUserSDK.getCurrentUser()?.uid ?: CoreUserSDK.silentLogin().uid
            }
            val uid = uidResult.getOrElse { error ->
                mainHandler.post {
                    callback(PurchaseResult(selectedChannel, product, false, message = error.message))
                }
                return@execute
            }
            mainHandler.post {
                provider.purchase(
                    activity = activity,
                    request = PurchaseRequest(product = product, uid = uid, paymentContext = paymentContext)
                ) purchaseCallback@ { providerResult ->
                    if (!providerResult.success) {
                        callback(
                            PurchaseResult(
                                channel = providerResult.channel,
                                product = providerResult.product,
                                success = false,
                                orderId = providerResult.orderId,
                                message = providerResult.message
                            )
                        )
                        return@purchaseCallback
                    }
                    paymentExecutor.execute {
                        val result = runCatching {
                            handleProviderPurchaseResult(provider, providerResult, uid)
                        }.getOrElse { error ->
                            PurchaseResult(
                                channel = providerResult.channel,
                                product = providerResult.product,
                                success = false,
                                orderId = providerResult.orderId,
                                message = error.message
                            )
                        }
                        mainHandler.post { callback(result) }
                    }
                }
            }
        }
    }

    fun restore(channel: PaymentChannel = PaymentChannel.GOOGLE_PLAY): RestoreResult {
        val provider = providers[channel]
            ?: throw PaymentException("Payment provider is not registered for ${channel.wireValue}")
        if (channel !in getAvailableChannels() && channel != PaymentChannel.MOCK) {
            throw PaymentException("Payment channel is disabled: ${channel.wireValue}")
        }
        val restoreResult = provider.restore()
        val uid = CoreUserSDK.getCurrentUser()?.uid ?: CoreUserSDK.silentLogin().uid
        restoreResult.purchases.forEach { purchase ->
            if (purchase.success && purchase.orderId != null) {
                val verification = if (provider.requiresServerVerification) verifyProviderPurchase(purchase, uid) else null
                if (verification == null || verification.isSuccessful) {
                    if (purchase.channel == PaymentChannel.GOOGLE_PLAY) {
                        settleGooglePlayPurchase(purchase)
                    }
                    entitlementStore.grant(
                        purchase.product,
                        purchase.orderId,
                        purchase.channel,
                        verification,
                        uid = uid
                    )
                }
            }
        }
        return restoreResult
    }

    fun getEntitlements(): List<Entitlement> {
        val uid = CoreUserSDK.getCurrentUser()?.uid ?: return emptyList()
        return entitlementStore.getEntitlements(uid)
    }

    fun getWeeklyPointsInfo(): WeeklyPointsInfo = CoreUserSDK.getWeeklyPointsInfo()

    fun claimWeeklyPoints(marketProductId: String? = null): WeeklyPointsClaimResult {
        val result = CoreUserSDK.claimWeeklyPoints(marketProductId)
        if (result.success) {
            runCatching { CoreUserSDK.fetchUserInfo() }
                .onSuccess { user ->
                    Log.d(TAG, "Weekly points balance refreshed balance=${user.balance}")
                }
                .onFailure { error ->
                    Log.w(TAG, "Weekly points claimed but user balance refresh failed", error)
                }
        }
        return result
    }

    fun showSubscriptionPage(context: Context, config: SubscriptionPageConfig) {
        requireConfig()
        activeSubscriptionPageConfig = config
        context.startActivity(
            Intent(context, SubscriptionPageActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun closeSubscriptionPage() {
        subscriptionPageActivity?.get()?.let { activity ->
            if (!activity.isFinishing) {
                activity.finish()
            }
        }
        subscriptionPageActivity = null
    }

    fun onSubscriptionPageEvent(callback: (SubscriptionPageEvent) -> Unit): () -> Unit {
        subscriptionPageCallbacks.add(callback)
        return { subscriptionPageCallbacks.remove(callback) }
    }

    internal fun getActiveSubscriptionPageConfig(): SubscriptionPageConfig {
        return activeSubscriptionPageConfig
            ?: throw PaymentException("Subscription page config is not available")
    }

    internal fun attachSubscriptionPage(activity: Activity) {
        subscriptionPageActivity = WeakReference(activity)
    }

    internal fun detachSubscriptionPage(activity: Activity) {
        if (subscriptionPageActivity?.get() === activity) {
            subscriptionPageActivity = null
        }
    }

    internal fun dispatchSubscriptionPageEvent(event: SubscriptionPageEvent) {
        subscriptionPageCallbacks.toList().forEach { callback -> callback(event) }
        if (GrowthAnalyticsAdSDK.isInitialized()) {
            GrowthAnalyticsAdSDK.reportPurchaseEvent(event.analyticsName(), event.analyticsParams())
        }
    }

    private fun requireConfig(): PaymentConfig {
        return config ?: throw PaymentException("PaymentSDK is not initialized")
    }

    private fun requireProductApi(): ProductApi {
        return productApi ?: throw PaymentException("PaymentSDK is not initialized")
    }

    private fun requireOrderVerificationApi(): OrderVerificationApi {
        return orderVerificationApi ?: throw PaymentException("PaymentSDK is not initialized")
    }

    private fun mergeBillingProducts(apiProducts: List<Product>): List<Product> {
        if (apiProducts.isEmpty()) return emptyList()
        val googleProvider = googlePlayBillingProvider
        val paymentConfig = requireConfig()
        val requestedChannels = activeSubscriptionPageConfig?.paymentChannels
            ?.takeIf { it.isNotEmpty() }
            ?: paymentConfig.enabledChannels
        if (googleProvider == null || PaymentChannel.GOOGLE_PLAY !in requestedChannels) {
            return apiProducts
        }
        val billingProducts = googleProvider.getProducts(apiProducts.map { it.marketProductId })
        if (billingProducts.isEmpty()) {
            Log.w(TAG, "Google Play returned no matching product details; keeping API products")
            return apiProducts
        }
        return ProductDetailsMerger.merge(apiProducts, billingProducts)
    }

    private fun verifyProviderPurchase(result: ProviderPurchaseResult, uid: String): OrderVerificationResult {
        val token = result.purchaseToken
            ?: throw PaymentException("Purchase token is required for server verification")
        val orderId = result.orderId
            ?: throw PaymentException("Order id is required for server verification")
        return requireOrderVerificationApi().verify(
            OrderVerificationRequest(
                channel = result.channel,
                token = token,
                platformProductId = result.platformProductId,
                uid = uid,
                isSubscription = result.isSubscription,
                tradeOrderId = orderId
            )
        )
    }

    private fun handleProviderPurchaseResult(
        provider: PaymentProvider,
        providerResult: ProviderPurchaseResult,
        uid: String
    ): PurchaseResult {
        val product = providerResult.product
        if (!providerResult.success) {
            return PurchaseResult(
                channel = providerResult.channel,
                product = product,
                success = false,
                orderId = providerResult.orderId,
                message = providerResult.message
            )
        }
        val orderId = providerResult.orderId
            ?: throw PaymentException("Purchase succeeded without order id")
        val verification = if (provider.requiresServerVerification) {
            verifyProviderPurchase(providerResult, uid)
        } else {
            OrderVerificationResult(
                tradeOrderId = orderId,
                status = OrderStatus.SUCCESS,
                isSubscription = providerResult.isSubscription,
                token = providerResult.purchaseToken,
                platformProductId = providerResult.platformProductId
            )
        }
        if (!verification.isSuccessful) {
            return PurchaseResult(
                channel = providerResult.channel,
                product = product,
                success = false,
                orderId = orderId,
                message = "Order verification status: ${verification.status}",
                verification = verification
            )
        }
        if (providerResult.channel == PaymentChannel.GOOGLE_PLAY) {
            settleGooglePlayPurchase(providerResult)
        }
        val entitlement = entitlementStore.grant(
            product,
            orderId,
            providerResult.channel,
            verification,
            uid = uid
        )
        if (entitlement != null) {
            reportPurchaseRevenue(product, orderId, providerResult.channel, verification)
        }
        return PurchaseResult(
            channel = providerResult.channel,
            product = product,
            success = true,
            orderId = orderId,
            message = providerResult.message,
            verification = verification,
            entitlement = entitlement
        )
    }

    private fun settleGooglePlayPurchase(result: ProviderPurchaseResult) {
        val settlement = googlePlayBillingProvider?.settlePurchase(result)
            ?: throw PaymentException("Google Play Billing provider is not available")
        if (!settlement.success) {
            throw PaymentException(
                settlement.message?.takeIf { it.isNotBlank() }
                    ?: "Google Play purchase settlement failed"
            )
        }
    }

    private fun processRecoveredGooglePlayPurchase(result: ProviderPurchaseResult) {
        if (!result.success) return
        paymentExecutor.execute {
            runCatching {
                val availableProducts = if (products.isEmpty()) getProducts(forceRefresh = true) else products
                val product = availableProducts.firstOrNull { it.marketProductId == result.platformProductId }
                    ?: return@runCatching
                val uid = CoreUserSDK.getCurrentUser()?.uid ?: CoreUserSDK.silentLogin().uid
                handleProviderPurchaseResult(
                    requireNotNull(googlePlayBillingProvider),
                    result.copy(
                        product = product,
                        platformProductId = product.marketProductId,
                        isSubscription = product.productType == ProductType.SUBSCRIPTION
                    ),
                    uid
                )
            }.onFailure { error ->
                Log.e(TAG, "Google Play recovered purchase processing failed", error)
            }
        }
    }

    private fun reconcileGooglePlayPurchases() {
        paymentExecutor.execute {
            runCatching {
                val provider = googlePlayBillingProvider ?: return@runCatching
                val availableProducts = getProducts(forceRefresh = true)
                val productsById = availableProducts.associateBy { it.marketProductId }
                val uid = CoreUserSDK.getCurrentUser()?.uid ?: CoreUserSDK.silentLogin().uid
                provider.restore().purchases
                    .filter { it.success }
                    .forEach { purchase ->
                        val product = productsById[purchase.platformProductId] ?: return@forEach
                        handleProviderPurchaseResult(
                            provider,
                            purchase.copy(
                                product = product,
                                isSubscription = product.productType == ProductType.SUBSCRIPTION
                            ),
                            uid
                        )
                    }
            }.onFailure { error ->
                Log.e(TAG, "Google Play startup reconciliation failed", error)
            }
        }
    }

    private fun reportPurchaseRevenue(
        product: Product,
        orderId: String,
        channel: PaymentChannel,
        verification: OrderVerificationResult?
    ) {
        val currency = product.currency ?: return
        val revenue = verification?.amount?.toDoubleOrNull()
            ?: product.price?.toDoubleOrNull()
            ?: return
        if (!GrowthAnalyticsAdSDK.isInitialized()) return
        GrowthAnalyticsAdSDK.reportPurchaseRevenue(
            PurchaseRevenuePayload(
                paymentChannel = channel.toGrowthPaymentChannel(),
                orderId = orderId,
                storeProductId = product.marketProductId,
                purchaseType = if (product.productType == ProductType.SUBSCRIPTION) {
                    PurchaseType.SUBSCRIPTION
                } else {
                    PurchaseType.ONE_TIME
                },
                subscriptionPeriod = product.subscriptionPeriod,
                currency = currency,
                revenue = revenue
            )
        )
    }

    private fun PaymentChannel.toGrowthPaymentChannel(): GrowthPaymentChannel {
        return when (this) {
            PaymentChannel.GOOGLE_PLAY -> GrowthPaymentChannel.GOOGLE_PLAY
            PaymentChannel.APP_STORE -> GrowthPaymentChannel.APP_STORE
            PaymentChannel.STRIPE,
            PaymentChannel.PAYPAL,
            PaymentChannel.WEB_CHECKOUT,
            PaymentChannel.MOCK -> GrowthPaymentChannel.GOOGLE_PLAY
        }
    }
}
