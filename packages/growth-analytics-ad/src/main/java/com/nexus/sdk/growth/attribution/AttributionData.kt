package com.nexus.sdk.growth.attribution

data class AttributionData(
    val source: String? = null,
    val campaign: String? = null,
    val medium: String? = null,
    val channel: String? = null,
    val adset: String? = null,
    val ad: String? = null,
    val afStatus: String? = null,
    val mediaSource: String? = null,
    val deepLinkValue: String? = null,
    val target: String? = null,
    val rawParams: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
