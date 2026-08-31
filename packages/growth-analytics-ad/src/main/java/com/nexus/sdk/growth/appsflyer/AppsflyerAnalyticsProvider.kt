package com.nexus.sdk.growth.appsflyer

import android.content.Context
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkListener
import com.appsflyer.deeplink.DeepLinkResult
import com.nexus.sdk.growth.attribution.AttributionMapper
import com.nexus.sdk.growth.attribution.AttributionStorage
import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.nexus.sdk.growth.event_router.AnalyticsProvider
import com.nexus.sdk.growth.appsflyer.AppsflyerAdEventMapper

internal class AppsflyerAnalyticsProvider(
    context: Context,
    devKey: String,
    private val attributionStorage: AttributionStorage?
) : AnalyticsProvider {
    override val name: String = "appsflyer"
    private val appContext = context.applicationContext
    private val appsFlyer = AppsFlyerLib.getInstance()
    private var userProperties: Map<String, Any?> = emptyMap()

    init {
        appsFlyer.init(devKey, conversionListener(), appContext)
        appsFlyer.subscribeForDeepLink(deepLinkListener())
        appsFlyer.start(appContext)
    }

    override fun track(event: AnalyticsEvent) {
        event.uid?.let { appsFlyer.setCustomerUserId(it) }
        val appsflyerEvent = AppsflyerAdEventMapper.map(event) ?: return
        appsFlyer.logEvent(appContext, appsflyerEvent.name, appsflyerEvent.params)
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        userProperties = properties
        appsFlyer.setAdditionalData(properties.mapValues { it.value ?: "" })
    }

    override fun flush() = Unit

    private fun conversionListener(): AppsFlyerConversionListener {
        return object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                if (data != null) {
                    attributionStorage?.saveInstallSource(AttributionMapper.fromConversionData(data))
                }
            }

            override fun onConversionDataFail(error: String?) = Unit

            override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                if (data != null) {
                    attributionStorage?.saveInstallSource(
                        AttributionMapper.fromConversionData(data.mapValues { it.value })
                    )
                }
            }

            override fun onAttributionFailure(error: String?) = Unit
        }
    }

    private fun deepLinkListener(): DeepLinkListener {
        return DeepLinkListener { result ->
            if (result.status == DeepLinkResult.Status.FOUND) {
                result.deepLink?.let { deepLink ->
                    val deepLinkResult = AttributionMapper.fromDeepLink(deepLink)
                    attributionStorage?.saveDeepLink(deepLinkResult)
                    attributionStorage?.saveInstallSource(AttributionMapper.fromDeepLinkResult(deepLinkResult))
                }
            }
        }
    }
}
