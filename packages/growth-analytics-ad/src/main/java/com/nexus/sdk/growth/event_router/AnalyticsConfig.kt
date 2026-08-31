package com.nexus.sdk.growth.event_router

data class AnalyticsConfig(
    val productId: String,
    val platform: String = "android",
    val enableBI: Boolean = true,
    val enableFirebase: Boolean = true,
    val enableAppsflyer: Boolean = true,
    val enableAdMob: Boolean = true,
    val dataEyeAppId: String? = null,
    val dataEyeServerUrl: String? = null,
    val appsflyerDevKey: String? = null,
    val queueMaxSize: Int = 500,
    val debug: Boolean = false
) {
    init {
        require(productId.isNotBlank()) { "productId is required" }
        require(platform.isNotBlank()) { "platform is required" }
        require(queueMaxSize > 0) { "queueMaxSize must be greater than 0" }
    }
}
