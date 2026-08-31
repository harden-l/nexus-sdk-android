package com.nexus.sdk.growth.event_router

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.nexus.sdk.coreuser.silent_login.SDKUser
import com.nexus.sdk.growth.attribution.AttributionData
import com.nexus.sdk.growth.attribution.AttributionMapper
import com.nexus.sdk.growth.attribution.AttributionStorage
import com.nexus.sdk.growth.attribution.DeepLinkParser
import com.nexus.sdk.growth.attribution.DeepLinkResult
import com.nexus.sdk.growth.ads.AdCallbacks
import com.nexus.sdk.growth.ads.AdMobAdProvider
import com.nexus.sdk.growth.ads.AdPlacement
import com.nexus.sdk.growth.ads.AdProvider
import com.nexus.sdk.growth.ads.MockAdProvider
import com.nexus.sdk.growth.ads.NativeAdCallbacks
import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.nexus.sdk.growth.event_router.AnalyticsProvider
import com.nexus.sdk.growth.event_router.EventRouter
import com.nexus.sdk.growth.event_router.QueuedAnalyticsProvider
import com.nexus.sdk.growth.appsflyer.AppsflyerAnalyticsProvider
import com.nexus.sdk.growth.bi.DataEyeAnalyticsProvider
import com.nexus.sdk.growth.firebase.FirebaseAnalyticsProvider
import com.nexus.sdk.growth.event_router.MockAnalyticsProvider
import com.nexus.sdk.growth.ad_revenue.AdRevenuePayload
import com.nexus.sdk.growth.purchase_revenue.PaymentChannel
import com.nexus.sdk.growth.purchase_revenue.PurchaseRevenuePayload
import com.nexus.sdk.growth.bi.UserPropertiesStore
import com.google.android.gms.ads.AdSize

object GrowthAnalyticsAdSDK {
    const val VERSION = "0.0.12"
    private const val TAG = "GrowthAnalyticsAdSDK"

    private var config: AnalyticsConfig? = null
    private var currentUser: SDKUser? = null
    private var router: EventRouter? = null
    private var providers: List<AnalyticsProvider> = emptyList()
    private var adProvider: AdProvider? = null
    private var attributionStorage: AttributionStorage? = null
    private val userPropertiesStore = UserPropertiesStore()
    private val reportedPurchaseKeys = mutableSetOf<String>()
    private var activityProvider: () -> Activity? = { null }

    @Synchronized
    fun init(
        config: AnalyticsConfig,
        providers: List<AnalyticsProvider>? = null,
        adProvider: AdProvider? = null
    ) {
        this.config = config
        this.providers = providers ?: createDefaultProviders(config)
        this.router = EventRouter(this.providers)
        this.adProvider = adProvider ?: MockAdProvider()
        this.attributionStorage = null
        reportedPurchaseKeys.clear()
        debugLog("initialized with providers=${this.providers.map { it.name }}")
    }

    @Synchronized
    fun init(
        context: Context,
        config: AnalyticsConfig,
        activityProvider: () -> Activity? = { null }
    ) {
        this.activityProvider = activityProvider
        val appContext = context.applicationContext
        this.config = config
        this.attributionStorage = AttributionStorage(appContext)
        this.providers = createRealProviders(appContext, config)
        this.router = EventRouter(this.providers)
        this.adProvider = if (config.enableAdMob) {
            AdMobAdProvider(
                context = appContext,
                activityProvider = activityProvider,
                revenueReporter = { payload -> reportAdRevenue(payload) },
                eventReporter = { eventName, params -> track(eventName, params) }
            )
        } else {
            MockAdProvider()
        }
        reportedPurchaseKeys.clear()
        debugLog("initialized with providers=${this.providers.map { it.name }}, enableAdMob=${config.enableAdMob}")
    }

    fun setUser(user: SDKUser?) {
        currentUser = user
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        userPropertiesStore.set(properties)
        providers.forEach { provider ->
            if (provider is FirebaseAnalyticsProvider) {
                provider.setUserProperties(userPropertiesStore.snapshot())
            }
            if (provider is DataEyeAnalyticsProvider) {
                provider.setUserProperties(userPropertiesStore.snapshot())
            }
            if (provider is AppsflyerAnalyticsProvider) {
                provider.setUserProperties(userPropertiesStore.snapshot())
            }
        }
    }

    fun getInstallSource(): AttributionData? = attributionStorage?.getInstallSource()

    fun getLastDeepLink(): DeepLinkResult? = attributionStorage?.getLastDeepLink()

    fun handleDeepLink(url: String): DeepLinkResult {
        val result = DeepLinkParser.parse(url)
        attributionStorage?.saveDeepLink(result)
        attributionStorage?.saveInstallSource(AttributionMapper.fromDeepLinkResult(result))
        return result
    }

    fun track(eventName: String, params: Map<String, Any?> = emptyMap()): AnalyticsEvent {
        val currentConfig = requireConfig()
        val user = currentUser
        val event = AnalyticsEvent(
            eventName = eventName,
            uid = user?.uid,
            deviceId = user?.deviceId,
            productId = currentConfig.productId,
            platform = currentConfig.platform,
            timestamp = System.currentTimeMillis(),
            params = userPropertiesStore.snapshot() + params
        )
        debugLog("track event=${event.eventName}, uid=${event.uid}, deviceId=${event.deviceId}, params=${event.params}")
        requireRouter().track(event)
        return event
    }

    fun flush() {
        requireRouter().flush()
    }

    fun getProviders(): List<AnalyticsProvider> = providers

    fun isInitialized(): Boolean = config != null && router != null

    fun loadAd(placement: AdPlacement, callbacks: AdCallbacks? = null) {
        track(
            eventName = "ad_load",
            params = placement.toEventParams()
        )
        requireAdProvider().loadAd(placement, callbacks)
    }

    fun showAd(placement: AdPlacement, callbacks: AdCallbacks? = null) {
        track(
            eventName = "ad_show",
            params = placement.toEventParams()
        )
        requireAdProvider().showAd(placement, callbacks)
    }

    fun loadBanner(
        placement: AdPlacement,
        container: ViewGroup,
        adSize: AdSize = AdSize.BANNER,
        callbacks: AdCallbacks? = null
    ) {
        track(
            eventName = "ad_load",
            params = placement.toEventParams()
        )
        (requireAdProvider() as? AdMobAdProvider)
            ?.loadBanner(placement, container, adSize, callbacks)
            ?: callbacks?.onFailed(placement, IllegalStateException("Current AdProvider does not support banner ads"))
    }

    fun loadNative(
        placement: AdPlacement,
        callbacks: NativeAdCallbacks
    ) {
        track(
            eventName = "ad_load",
            params = placement.toEventParams()
        )
        (requireAdProvider() as? AdMobAdProvider)
            ?.loadNative(placement, callbacks)
            ?: callbacks.onFailed(placement, IllegalStateException("Current AdProvider does not support native ads"))
    }

    fun reportAdRevenue(payload: AdRevenuePayload): AnalyticsEvent {
        return track(
            eventName = "ad_revenue",
            params = mapOf(
                "revenue_type" to "ad",
                "ad_platform" to payload.adPlatform,
                "mediation_platform" to payload.mediationPlatform,
                "ad_unit_id" to payload.adUnitId,
                "placement" to payload.placement,
                "ad_format" to payload.adFormat.wireValue,
                "currency" to payload.currency,
                "revenue" to payload.revenue,
                "country" to payload.country,
                "network_name" to payload.networkName,
                "network_firm_id" to payload.networkFirmId,
                "scene" to payload.scene,
                "precision" to payload.precision
            )
        )
    }

    fun reportPurchaseRevenue(payload: PurchaseRevenuePayload): AnalyticsEvent? {
        val key = payload.dedupeKey()
        if (!reportedPurchaseKeys.add(key)) return null

        return track(
            eventName = "purchase_revenue",
            params = mapOf(
                "revenue_type" to revenueTypeFor(payload.paymentChannel),
                "payment_channel" to payload.paymentChannel.wireValue,
                "order_id" to payload.orderId,
                "transaction_id" to payload.transactionId,
                "store_product_id" to payload.storeProductId,
                "purchase_type" to payload.purchaseType.wireValue,
                "subscription_period" to payload.subscriptionPeriod,
                "currency" to payload.currency,
                "revenue" to payload.revenue,
                "country" to payload.country,
                "is_trial" to payload.isTrial,
                "is_renewal" to payload.isRenewal
            )
        )
    }

    fun reportPurchaseEvent(eventName: String, payload: Map<String, Any?>): AnalyticsEvent {
        return track(eventName = eventName, params = payload)
    }

    private fun requireConfig(): AnalyticsConfig {
        return config ?: throw IllegalStateException("GrowthAnalyticsAdSDK is not initialized")
    }

    private fun requireRouter(): EventRouter {
        return router ?: throw IllegalStateException("GrowthAnalyticsAdSDK is not initialized")
    }

    private fun requireAdProvider(): AdProvider {
        return adProvider ?: throw IllegalStateException("GrowthAnalyticsAdSDK is not initialized")
    }

    private fun createDefaultProviders(config: AnalyticsConfig): List<AnalyticsProvider> {
        return buildList {
            if (config.enableBI) add(MockAnalyticsProvider("bi"))
            if (config.enableFirebase) add(MockAnalyticsProvider("firebase"))
            if (config.enableAppsflyer) add(MockAnalyticsProvider("appsflyer"))
        }
    }

    private fun createRealProviders(context: Context, config: AnalyticsConfig): List<AnalyticsProvider> {
        return buildList {
            if (config.enableBI) {
                val appId = config.dataEyeAppId
                if (!appId.isNullOrBlank()) {
                    add(
                        QueuedAnalyticsProvider(
                            context,
                            DataEyeAnalyticsProvider(context, appId, config.dataEyeServerUrl),
                            config.queueMaxSize
                        )
                    )
                } else {
                    add(
                        QueuedAnalyticsProvider(
                            context,
                            MockAnalyticsProvider("dataeye"),
                            config.queueMaxSize
                        )
                    )
                }
            }
            if (config.enableFirebase) {
                add(
                    QueuedAnalyticsProvider(
                        context,
                        FirebaseAnalyticsProvider(context),
                        config.queueMaxSize
                    )
                )
            }
            if (config.enableAppsflyer) {
                val devKey = config.appsflyerDevKey
                if (!devKey.isNullOrBlank()) {
                    add(
                        QueuedAnalyticsProvider(
                            context,
                            AppsflyerAnalyticsProvider(
                                context = context,
                                devKey = devKey,
                                attributionStorage = attributionStorage
                            ),
                            config.queueMaxSize
                        )
                    )
                } else {
                    add(
                        QueuedAnalyticsProvider(
                            context,
                            MockAnalyticsProvider("appsflyer"),
                            config.queueMaxSize
                        )
                    )
                }
            }
        }
    }

    private fun revenueTypeFor(channel: PaymentChannel): String {
        return when (channel) {
            PaymentChannel.APP_STORE,
            PaymentChannel.GOOGLE_PLAY -> "iap"
            PaymentChannel.STRIPE,
            PaymentChannel.PAYPAL,
            PaymentChannel.WEB_CHECKOUT -> "third_party_payment"
        }
    }

    private fun AdPlacement.toEventParams(): Map<String, Any?> {
        return mapOf(
            "placement" to placement,
            "ad_unit_id" to adUnitId,
            "ad_format" to format.wireValue,
            "frequency_cap" to frequencyCap
        )
    }

    private fun debugLog(message: String) {
        if (config?.debug == true) {
            Log.d(TAG, message)
        }
    }
}
