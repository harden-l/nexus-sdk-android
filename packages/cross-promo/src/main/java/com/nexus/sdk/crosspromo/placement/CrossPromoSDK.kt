package com.nexus.sdk.crosspromo.placement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.nexus.sdk.coreuser.init.CoreUserSDK
import com.nexus.sdk.coreuser.network.RelatedProduct
import com.nexus.sdk.crosspromo.attribution.CrossPromoAttributionStorage
import com.nexus.sdk.crosspromo.deeplink.CrossPromoLinkParser
import com.nexus.sdk.crosspromo.promo_template.CrossPromoActivity
import com.nexus.sdk.growth.event_router.GrowthAnalyticsAdSDK
import java.util.UUID

object CrossPromoSDK {
    const val VERSION = "0.0.12"
    private const val TAG = "CrossPromoSDK"

    private var appContext: Context? = null
    private var config: CrossPromoConfig? = null
    private var attributionStorage: CrossPromoAttributionStorage? = null
    private var activePageOptions: ShowPromoPageOptions = ShowPromoPageOptions()
    private var relatedProducts: List<CrossPromoProduct> = emptyList()

    fun init(context: Context, config: CrossPromoConfig) {
        this.appContext = context.applicationContext
        this.config = config
        this.attributionStorage = CrossPromoAttributionStorage(context.applicationContext)
        this.relatedProducts = emptyList()
        Log.d(TAG, "initialized sourceProductId=${config.sourceProductId}")
    }

    fun showPromoPage(context: Context, options: ShowPromoPageOptions = ShowPromoPageOptions()) {
        requireConfig()
        activePageOptions = options
        track("cross_promo_show", baseParams(options.placement, options.campaign))
        context.startActivity(
            Intent(context, CrossPromoActivity::class.java).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openProduct(options: OpenProductOptions): Boolean {
        val product = getProductsForDisplay().firstOrNull { it.productId == options.productId }
            ?: throw CrossPromoException("Cross promo product not found: ${options.productId}")
        return openProduct(product, options.placement, options.campaign)
    }

    fun handleIncomingPromoLink(url: String): CrossPromoLinkResult {
        val result = CrossPromoLinkParser.parse(url)
        requireAttributionStorage().save(result)
        runCatching { CoreUserSDK.setLoginAttributionEnabled(true) }
        track(
            "cross_promo_activate",
            mapOf(
                "click_id" to result.clickId,
                "source_product_id" to result.sourceProductId,
                "target_product_id" to result.targetProductId,
                "placement" to result.placement,
                "campaign" to result.campaign,
                "source_uid" to result.sourceUid,
                "source_device_id" to result.sourceDeviceId,
                "raw_url" to result.rawUrl
            )
        )
        return result
    }

    fun getPendingAttribution(): CrossPromoLinkResult? {
        return requireAttributionStorage().get()
    }

    fun clearPendingAttribution() {
        requireAttributionStorage().clear()
        runCatching { CoreUserSDK.setLoginAttributionEnabled(false) }
    }

    fun flushPendingAttributionAfterLogin(): CrossProductUserLinkPayload? {
        val pending = getPendingAttribution() ?: return null
        val user = CoreUserSDK.getCurrentUser() ?: return null
        val payload = CrossProductUserLinkPayload(
            clickId = pending.clickId,
            sourceProductId = pending.sourceProductId ?: return null,
            targetProductId = pending.targetProductId ?: requireConfig().sourceProductId,
            sourceUid = pending.sourceUid,
            targetUid = user.uid,
            sourceDeviceId = pending.sourceDeviceId,
            targetDeviceId = user.deviceId,
            email = user.email,
            placement = pending.placement,
            campaign = pending.campaign
        )
        linkCrossProductUser(payload)
        clearPendingAttribution()
        return payload
    }

    fun linkCrossProductUser(payload: CrossProductUserLinkPayload): CrossProductUserLinkPayload {
        track(
            "cross_promo_user_link",
            mapOf(
                "click_id" to payload.clickId,
                "source_product_id" to payload.sourceProductId,
                "target_product_id" to payload.targetProductId,
                "source_uid" to payload.sourceUid,
                "target_uid" to payload.targetUid,
                "source_device_id" to payload.sourceDeviceId,
                "target_device_id" to payload.targetDeviceId,
                "email" to payload.email,
                "placement" to payload.placement,
                "campaign" to payload.campaign
            )
        )
        return payload
    }

    internal fun getConfig(): CrossPromoConfig = requireConfig()

    internal fun getActivePageOptions(): ShowPromoPageOptions = activePageOptions

    internal fun getProductsForDisplay(forceRefresh: Boolean = false): List<CrossPromoProduct> {
        if (relatedProducts.isNotEmpty() && !forceRefresh) return relatedProducts
        val allProducts = CoreUserSDK.getRelatedProducts()
        relatedProducts = allProducts
            .filterNot(::isCurrentProduct)
            .map(::toCrossPromoProduct)
        Log.d(
            TAG,
            "related products loaded raw=${allProducts.size}, filtered=${relatedProducts.size}, " +
                "sourceProductId=${requireConfig().sourceProductId}, package=${requireContext().packageName}"
        )
        return relatedProducts
    }

    internal fun isProductInstalled(product: CrossPromoProduct): Boolean {
        return isPackageInstalled(product.androidPackageName)
    }

    internal fun openProduct(
        product: CrossPromoProduct,
        placement: String?,
        campaign: String?
    ): Boolean {
        val context = requireContext()
        val clickId = generateClickId()
        val params = baseParams(placement, campaign) + mapOf(
            "click_id" to clickId,
            "target_product_id" to product.productId
        )
        track("cross_promo_click", params)

        val openedDeepLink = product.deepLinkUrl
            ?.takeIf { isPackageInstalled(product.androidPackageName) }
            ?.let { openUri(context, withAttributionParams(it, params)) }
            ?: false

        if (openedDeepLink) {
            track("cross_promo_open", params + ("link_type" to "deep_link"))
            return true
        }

        val openedInstalledApp = product.androidPackageName
            ?.takeIf { isPackageInstalled(it) }
            ?.let { openInstalledPackage(context, it) }
            ?: false
        if (openedInstalledApp) {
            track("cross_promo_open", params + ("link_type" to "package"))
            return true
        }

        val storeUrl = product.storeUrl ?: product.androidPackageName?.let { "market://details?id=$it" }
        if (storeUrl.isNullOrBlank()) {
            track("cross_promo_open_failed", params + ("message" to "No deep link or store url configured"))
            return false
        }

        val openedStore = openUri(context, withStoreReferrer(storeUrl, params)) ||
            product.androidPackageName?.let {
                openUri(context, withStoreReferrer("https://play.google.com/store/apps/details?id=$it", params))
            } == true
        track(
            if (openedStore) "cross_promo_store_open" else "cross_promo_open_failed",
            params + ("link_type" to "store")
        )
        return openedStore
    }

    private fun baseParams(placement: String?, campaign: String?): Map<String, Any?> {
        val promoConfig = requireConfig()
        val user = runCatching { CoreUserSDK.getCurrentUser() }.getOrNull()
        return mapOf(
            "source_product_id" to promoConfig.sourceProductId,
            "placement" to (placement ?: promoConfig.defaultPlacement),
            "campaign" to (campaign ?: promoConfig.campaign),
            "source_uid" to user?.uid,
            "source_device_id" to user?.deviceId
        )
    }

    private fun withAttributionParams(url: String, params: Map<String, Any?>): String {
        val uri = Uri.parse(url)
        val builder = uri.buildUpon()
        params.forEach { (key, value) ->
            if (value != null && uri.getQueryParameter(key).isNullOrBlank()) {
                builder.appendQueryParameter(key, value.toString())
            }
        }
        return builder.build().toString()
    }

    private fun withStoreReferrer(url: String, params: Map<String, Any?>): String {
        val uri = Uri.parse(url)
        if (!uri.getQueryParameter("referrer").isNullOrBlank()) return url
        val referrer = Uri.Builder().apply {
            params.forEach { (key, value) ->
                if (value != null) appendQueryParameter(key, value.toString())
            }
        }.build().encodedQuery.orEmpty()
        return uri.buildUpon()
            .appendQueryParameter("referrer", referrer)
            .build()
            .toString()
    }

    private fun generateClickId(): String {
        return "cp_${System.currentTimeMillis()}_${UUID.randomUUID()}"
    }

    private fun track(eventName: String, params: Map<String, Any?>) {
        Log.d(TAG, "track event=$eventName, params=${params.filterValues { it != null }}")
        if (GrowthAnalyticsAdSDK.isInitialized()) {
            GrowthAnalyticsAdSDK.track(eventName, params.filterValues { it != null })
        } else {
            Log.d(TAG, "skip event=$eventName because GrowthAnalyticsAdSDK is not initialized")
        }
    }

    private fun openUri(context: Context, url: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun openInstalledPackage(context: Context, packageName: String): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@runCatching false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun isPackageInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun requireConfig(): CrossPromoConfig {
        return config ?: throw CrossPromoException("CrossPromoSDK is not initialized")
    }

    private fun requireContext(): Context {
        return appContext ?: throw CrossPromoException("CrossPromoSDK is not initialized")
    }

    private fun requireAttributionStorage(): CrossPromoAttributionStorage {
        return attributionStorage ?: throw CrossPromoException("CrossPromoSDK is not initialized")
    }

    private fun isCurrentProduct(product: RelatedProduct): Boolean {
        val context = requireContext()
        return product.packageName == context.packageName ||
            product.productId == requireConfig().sourceProductId
    }

    private fun toCrossPromoProduct(product: RelatedProduct): CrossPromoProduct {
        return CrossPromoProduct(
            productId = product.productId,
            title = product.productName.ifBlank { product.productId },
            description = product.description,
            iconUrl = product.icon,
            androidPackageName = product.packageName.takeIf { it.isNotBlank() },
            deepLinkUrl = null,
            storeUrl = product.downloadUrl.takeIf { it.isNotBlank() }
                ?: product.packageName.takeIf { it.isNotBlank() }?.let { "market://details?id=$it" }
        )
    }
}

class CrossPromoException(message: String) : RuntimeException(message)
