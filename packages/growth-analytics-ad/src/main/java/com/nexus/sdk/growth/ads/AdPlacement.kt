package com.nexus.sdk.growth.ads

data class AdPlacement(
    val placement: String,
    val adUnitId: String,
    val format: AdFormat,
    val frequencyCap: Int? = null,
    var adPlatform: String? = null,
    var adPlatformId: String? = null,
) {
    init {
        require(placement.isNotBlank()) { "placement is required" }
        require(adUnitId.isNotBlank()) { "adUnitId is required" }
    }
}
