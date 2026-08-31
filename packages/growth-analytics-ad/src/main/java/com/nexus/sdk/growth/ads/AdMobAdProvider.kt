package com.nexus.sdk.growth.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.nexus.sdk.growth.ad_revenue.AdRevenuePayload
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdMobAdProvider(
    context: Context,
    private val activityProvider: () -> Activity?,
    private val revenueReporter: (AdRevenuePayload) -> Unit,
    private val eventReporter: (String, Map<String, Any?>) -> Unit = { _, _ -> }
) : AdProvider {
    override val name: String = "admob"
    private val appContext = context.applicationContext
    private val appOpenAds = mutableMapOf<AdCacheKey, AppOpenAd>()
    private val interstitialAds = mutableMapOf<AdCacheKey, InterstitialAd>()
    private val rewardedAds = mutableMapOf<AdCacheKey, RewardedAd>()
    private val fullScreenStates = mutableMapOf<AdCacheKey, AdLoadState>()
    private val loadCallbacks = mutableMapOf<AdCacheKey, MutableList<PendingLoadCallback>>()
    private val presentingKeys = mutableSetOf<AdCacheKey>()
    private val bannerAds = mutableMapOf<String, AdView>()
    private val frequencyController = AdFrequencyController()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        MobileAds.initialize(appContext)
    }

    override fun loadAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        runOnMain { loadFullScreenAd(placement, callbacks) }
    }

    private fun loadFullScreenAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        if (placement.format == AdFormat.BANNER || placement.format == AdFormat.NATIVE) {
            callbacks?.onFailed(
                placement,
                UnsupportedOperationException(
                    if (placement.format == AdFormat.BANNER) {
                        "Use loadBanner for banner ads"
                    } else {
                        "Use loadNative for native ads"
                    }
                )
            )
            return
        }
        val key = placement.cacheKey()
        when (fullScreenStates[key] ?: AdLoadState.IDLE) {
            AdLoadState.LOADED -> {
                callbacks?.onLoaded(placement)
                return
            }

            AdLoadState.LOADING, AdLoadState.SHOWING -> {
                enqueueLoadCallback(key, placement, callbacks)
                return
            }

            AdLoadState.IDLE -> enqueueLoadCallback(key, placement, callbacks)
        }
        fullScreenStates[key] = AdLoadState.LOADING
        when (placement.format) {
            AdFormat.APP_OPEN -> loadAppOpen(placement, attempt = 0)
            AdFormat.INTERSTITIAL -> loadInterstitial(placement, attempt = 0)
            AdFormat.REWARDED -> loadRewarded(placement, attempt = 0)
            AdFormat.BANNER, AdFormat.NATIVE -> Unit
        }
    }

    override fun showAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        runOnMain { showFullScreenAd(placement, callbacks) }
    }

    private fun showFullScreenAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        val activity = activityProvider()
        if (activity == null) {
            callbacks?.onFailed(
                placement,
                IllegalStateException("Activity is required to show AdMob ads")
            )
            return
        }
        if (!frequencyController.canShow(placement)) {
            callbacks?.onFailed(
                placement,
                IllegalStateException("Frequency cap reached for placement=${placement.placement}")
            )
            return
        }
        loadFullScreenAd(placement, callbacks = null)
        val key = placement.cacheKey()
        if (key in presentingKeys) {
            callbacks?.onFailed(placement, IllegalStateException("Ad is already showing"))
            return
        }

        when (placement.format) {
            AdFormat.APP_OPEN -> {
                val ad = appOpenAds.remove(key)
                if (ad == null) {
                    callbacks?.onFailed(placement, IllegalStateException("App open ad is not loaded"))
                } else {
                    fullScreenStates[key] = AdLoadState.SHOWING
                    presentingKeys.add(key)
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    ad.setOnPaidEventListener { value -> reportPaidEvent(placement, value) }
                    ad.fullScreenContentCallback = fullScreenCallbacks(
                        placement = placement,
                        callbacks = callbacks
                    )
                    ad.show(activity)
                }
            }

            AdFormat.INTERSTITIAL -> {
                val ad = interstitialAds.remove(key)
                if (ad == null) {
                    callbacks?.onFailed(placement, IllegalStateException("Interstitial ad is not loaded"))
                } else {
                    fullScreenStates[key] = AdLoadState.SHOWING
                    presentingKeys.add(key)
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    ad.setOnPaidEventListener { value -> reportPaidEvent(placement, value) }
                    ad.fullScreenContentCallback = fullScreenCallbacks(
                        placement = placement,
                        callbacks = callbacks
                    )
                    ad.show(activity)
                }
            }

            AdFormat.REWARDED -> {
                val ad = rewardedAds.remove(key)
                if (ad == null) {
                    callbacks?.onFailed(placement, IllegalStateException("Rewarded ad is not loaded"))
                } else {
                    fullScreenStates[key] = AdLoadState.SHOWING
                    presentingKeys.add(key)
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    ad.setOnPaidEventListener { value -> reportPaidEvent(placement, value) }
                    ad.fullScreenContentCallback = fullScreenCallbacks(placement, callbacks)
                    ad.show(activity) { callbacks?.onReward(placement) }
                }
            }

            AdFormat.BANNER -> callbacks?.onFailed(
                placement,
                UnsupportedOperationException("Banner ads are shown in their ViewGroup container after loadBanner")
            )

            AdFormat.NATIVE -> callbacks?.onFailed(
                placement,
                UnsupportedOperationException("Native ads must be rendered by the host after loadNative")
            )
        }
    }

    fun loadBanner(
        placement: AdPlacement,
        container: ViewGroup,
        adSize: AdSize = AdSize.BANNER,
        callbacks: AdCallbacks? = null
    ) {
        loadBanner(placement, container, adSize, callbacks, attempt = 0)
    }

    private fun loadBanner(
        placement: AdPlacement,
        container: ViewGroup,
        adSize: AdSize,
        callbacks: AdCallbacks?,
        attempt: Int
    ) {
        val adView = AdView(container.context).apply {
            adUnitId = placement.adUnitId
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    placement.updateAdSource(
                        responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                        responseInfo?.loadedAdapterResponseInfo?.adSourceId
                    )
                    reportAdInventory(placement)
                    callbacks?.onLoaded(placement)
                    callbacks?.onShown(placement)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    retryOrFail(placement, callbacks, RuntimeException(error.message), attempt) {
                        loadBanner(placement, container, adSize, callbacks, attempt + 1)
                    }
                }

                override fun onAdClicked() {
                    reportAdClick(placement)
                    callbacks?.onClicked(placement)
                }

                override fun onAdClosed() {
                    callbacks?.onClosed(placement)
                }
            }
            setOnPaidEventListener { value ->
                reportPaidEvent(placement, value)
            }
        }
        bannerAds[placement.placement]?.destroy()
        bannerAds[placement.placement] = adView
        container.removeAllViews()
        container.addView(
            adView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    fun loadNative(
        placement: AdPlacement,
        callbacks: NativeAdCallbacks
    ) {
        loadNative(placement, callbacks, attempt = 0)
    }

    private fun loadNative(
        placement: AdPlacement,
        callbacks: NativeAdCallbacks,
        attempt: Int
    ) {
        val loader = AdLoader.Builder(appContext, placement.adUnitId)
            .forNativeAd { nativeAd ->
                placement.updateAdSource(
                    nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                    nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceId
                )
                nativeAd.setOnPaidEventListener { value -> reportPaidEvent(placement, value) }
                reportAdInventory(placement)
                callbacks.onLoaded(placement, nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    retryOrFail(placement, callbacks, RuntimeException(error.message), attempt) {
                        loadNative(placement, callbacks, attempt + 1)
                    }
                }

                override fun onAdClicked() {
                    reportAdClick(placement)
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    private fun loadInterstitial(placement: AdPlacement, attempt: Int) {
        val key = placement.cacheKey()
        InterstitialAd.load(
            appContext,
            placement.adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    interstitialAds[key] = ad
                    fullScreenStates[key] = AdLoadState.LOADED
                    reportAdInventory(placement)
                    notifyLoadSucceeded(key)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    retryFullScreenLoad(placement, RuntimeException(error.message), attempt) {
                        loadInterstitial(placement, attempt + 1)
                    }
                }
            })
    }

    private fun loadAppOpen(placement: AdPlacement, attempt: Int) {
        val key = placement.cacheKey()
        AppOpenAd.load(
            appContext,
            placement.adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    appOpenAds[key] = ad
                    fullScreenStates[key] = AdLoadState.LOADED
                    reportAdInventory(placement)
                    notifyLoadSucceeded(key)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    retryFullScreenLoad(placement, RuntimeException(error.message), attempt) {
                        loadAppOpen(placement, attempt + 1)
                    }
                }
            })
    }

    private fun loadRewarded(placement: AdPlacement, attempt: Int) {
        val key = placement.cacheKey()
        RewardedAd.load(
            appContext,
            placement.adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    placement.updateAdSource(
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                        ad.responseInfo.loadedAdapterResponseInfo?.adSourceId
                    )
                    rewardedAds[key] = ad
                    fullScreenStates[key] = AdLoadState.LOADED
                    reportAdInventory(placement)
                    notifyLoadSucceeded(key)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    retryFullScreenLoad(placement, RuntimeException(error.message), attempt) {
                        loadRewarded(placement, attempt + 1)
                    }
                }
            })
    }

    private fun fullScreenCallbacks(
        placement: AdPlacement,
        callbacks: AdCallbacks?
    ): FullScreenContentCallback {
        val key = placement.cacheKey()
        return object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                frequencyController.markShown(placement)
                callbacks?.onShown(placement)
                startNextLoad(placement, key)
            }

            override fun onAdClicked() {
                reportAdClick(placement)
                callbacks?.onClicked(placement)
            }

            override fun onAdDismissedFullScreenContent() {
                callbacks?.onClosed(placement)
                presentingKeys.remove(key)
                if (fullScreenStates[key] == AdLoadState.SHOWING) {
                    startNextLoad(placement, key)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                callbacks?.onFailed(placement, RuntimeException(error.message))
                presentingKeys.remove(key)
                startNextLoad(placement, key)
            }
        }
    }

    private fun enqueueLoadCallback(
        key: AdCacheKey,
        placement: AdPlacement,
        callbacks: AdCallbacks?
    ) {
        if (callbacks == null) return
        loadCallbacks.getOrPut(key) { mutableListOf() }
            .add(PendingLoadCallback(placement, callbacks))
    }

    private fun notifyLoadSucceeded(key: AdCacheKey) {
        loadCallbacks.remove(key).orEmpty().forEach { pending ->
            pending.callbacks.onLoaded(pending.placement)
        }
    }

    private fun notifyLoadFailed(key: AdCacheKey, error: Throwable) {
        loadCallbacks.remove(key).orEmpty().forEach { pending ->
            pending.callbacks.onFailed(pending.placement, error)
        }
    }

    private fun retryFullScreenLoad(
        placement: AdPlacement,
        error: Throwable,
        attempt: Int,
        retry: () -> Unit
    ) {
        if (attempt < MAX_LOAD_RETRIES) {
            mainHandler.postDelayed(retry, RETRY_DELAY_MS)
            return
        }
        val key = placement.cacheKey()
        fullScreenStates[key] = AdLoadState.IDLE
        notifyLoadFailed(key, error)
    }

    private fun startNextLoad(placement: AdPlacement, key: AdCacheKey) {
        if (fullScreenStates[key] == AdLoadState.SHOWING) {
            fullScreenStates[key] = AdLoadState.IDLE
        }
        loadFullScreenAd(placement, callbacks = null)
    }

    private fun AdPlacement.cacheKey() = AdCacheKey(format, adUnitId)

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun reportPaidEvent(placement: AdPlacement, value: AdValue) {
        revenueReporter(
            AdRevenuePayload(
                adPlatform = "admob",
                mediationPlatform = "admob_mediation",
                adUnitId = placement.adUnitId,
                placement = placement.placement,
                adFormat = placement.format,
                currency = value.currencyCode,
                revenue = value.valueMicros / 1_000_000.0,
                networkName = placement.adPlatform,
                networkFirmId = placement.adPlatformId,
                scene = placement.placement,
                precision = value.precisionType.toString()
            )
        )
    }

    private fun reportAdInventory(placement: AdPlacement) {
        eventReporter("ad_inventory", placement.toAdEventParams())
    }

    private fun reportAdClick(placement: AdPlacement) {
        eventReporter("ad_click", placement.toAdEventParams())
    }

    private fun AdPlacement.toAdEventParams(): Map<String, Any?> {
        return mapOf(
            "placement" to placement,
            "ad_unit_id" to adUnitId,
            "ad_format" to format.wireValue,
            "ad_source_name" to adPlatform,
            "ad_source_id" to adPlatformId,
            "network_firm_id" to (adPlatformId ?: adPlatform ?: "admob")
        )
    }

    private fun AdPlacement.updateAdSource(name: String?, id: String?) {
        adPlatform = name?.takeIf { it.isNotBlank() } ?: "admob"
        adPlatformId = id?.takeIf { it.isNotBlank() } ?: adPlatform
    }

    private fun retryOrFail(
        placement: AdPlacement,
        callbacks: AdCallbacks?,
        error: Throwable,
        attempt: Int,
        retry: () -> Unit
    ) {
        if (attempt < MAX_LOAD_RETRIES) {
            mainHandler.postDelayed(retry, RETRY_DELAY_MS)
        } else {
            callbacks?.onFailed(placement, error)
        }
    }

    private fun retryOrFail(
        placement: AdPlacement,
        callbacks: NativeAdCallbacks,
        error: Throwable,
        attempt: Int,
        retry: () -> Unit
    ) {
        if (attempt < MAX_LOAD_RETRIES) {
            mainHandler.postDelayed(retry, RETRY_DELAY_MS)
        } else {
            callbacks.onFailed(placement, error)
        }
    }

    private companion object {
        private const val MAX_LOAD_RETRIES = 1
        private const val RETRY_DELAY_MS = 1_000L
    }

    private data class AdCacheKey(
        val format: AdFormat,
        val adUnitId: String
    )

    private data class PendingLoadCallback(
        val placement: AdPlacement,
        val callbacks: AdCallbacks
    )

    private enum class AdLoadState {
        IDLE,
        LOADING,
        LOADED,
        SHOWING
    }
}
