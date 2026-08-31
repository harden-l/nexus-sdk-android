package com.nexus.sdk.crosspromo.attribution

import android.content.Context
import com.nexus.sdk.crosspromo.placement.CrossPromoLinkResult

internal class CrossPromoAttributionStorage(context: Context) {
    private val preferences = context.getSharedPreferences("nexus_cross_promo_attribution", Context.MODE_PRIVATE)

    fun save(result: CrossPromoLinkResult) {
        preferences.edit()
            .putString(KEY_CLICK_ID, result.clickId)
            .putString(KEY_SOURCE_PRODUCT_ID, result.sourceProductId)
            .putString(KEY_TARGET_PRODUCT_ID, result.targetProductId)
            .putString(KEY_SOURCE_UID, result.sourceUid)
            .putString(KEY_SOURCE_DEVICE_ID, result.sourceDeviceId)
            .putString(KEY_PLACEMENT, result.placement)
            .putString(KEY_CAMPAIGN, result.campaign)
            .putString(KEY_RAW_URL, result.rawUrl)
            .apply()
    }

    fun get(): CrossPromoLinkResult? {
        val rawUrl = preferences.getString(KEY_RAW_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        return CrossPromoLinkResult(
            clickId = preferences.getString(KEY_CLICK_ID, null),
            sourceProductId = preferences.getString(KEY_SOURCE_PRODUCT_ID, null),
            targetProductId = preferences.getString(KEY_TARGET_PRODUCT_ID, null),
            sourceUid = preferences.getString(KEY_SOURCE_UID, null),
            sourceDeviceId = preferences.getString(KEY_SOURCE_DEVICE_ID, null),
            placement = preferences.getString(KEY_PLACEMENT, null),
            campaign = preferences.getString(KEY_CAMPAIGN, null),
            rawUrl = rawUrl
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_CLICK_ID = "click_id"
        private const val KEY_SOURCE_PRODUCT_ID = "source_product_id"
        private const val KEY_TARGET_PRODUCT_ID = "target_product_id"
        private const val KEY_SOURCE_UID = "source_uid"
        private const val KEY_SOURCE_DEVICE_ID = "source_device_id"
        private const val KEY_PLACEMENT = "placement"
        private const val KEY_CAMPAIGN = "campaign"
        private const val KEY_RAW_URL = "raw_url"
    }
}
