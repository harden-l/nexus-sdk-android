package com.nexus.sdk.growth.attribution

data class DeepLinkResult(
    val url: String,
    val source: String? = null,
    val campaign: String? = null,
    val medium: String? = null,
    val target: String? = null,
    val deepLinkValue: String? = null,
    val params: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
