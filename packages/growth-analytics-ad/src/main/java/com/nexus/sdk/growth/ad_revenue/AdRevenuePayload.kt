package com.nexus.sdk.growth.ad_revenue

import com.nexus.sdk.growth.ads.AdFormat

data class AdRevenuePayload(
    val adPlatform: String,
    val mediationPlatform: String? = null,
    val adUnitId: String,
    val placement: String,
    val adFormat: AdFormat,
    val currency: String,
    val revenue: Double,
    val country: String? = null,
    val networkName: String? = null,
    val networkFirmId: String? = null,
    val scene: String? = null,
    val precision: String? = null
) {
    init {
        require(adUnitId.isNotBlank()) { "adUnitId is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(revenue >= 0.0) { "revenue must not be negative" }
    }
}
