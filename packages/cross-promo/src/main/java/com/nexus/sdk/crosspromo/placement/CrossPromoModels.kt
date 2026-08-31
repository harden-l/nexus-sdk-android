package com.nexus.sdk.crosspromo.placement

data class CrossPromoConfig(
    val sourceProductId: String,
    val campaign: String = "internal_cross_promo",
    val defaultPlacement: String = "unknown"
) {
    init {
        require(sourceProductId.isNotBlank()) { "sourceProductId is required" }
    }
}

data class CrossPromoProduct(
    val productId: String,
    val title: String,
    val description: String = "",
    val iconUrl: String = "",
    val androidPackageName: String? = null,
    val deepLinkUrl: String? = null,
    val storeUrl: String? = null,
    val campaign: String? = null
) {
    init {
        require(productId.isNotBlank()) { "productId is required" }
        require(title.isNotBlank()) { "title is required" }
    }
}

data class ShowPromoPageOptions(
    val placement: String? = null,
    val campaign: String? = null,
    val title: String = "Recommended Apps",
    val description: String = ""
)

data class OpenProductOptions(
    val productId: String,
    val placement: String? = null,
    val campaign: String? = null
)

data class CrossPromoLinkResult(
    val clickId: String? = null,
    val sourceProductId: String? = null,
    val targetProductId: String? = null,
    val placement: String? = null,
    val campaign: String? = null,
    val sourceUid: String? = null,
    val sourceDeviceId: String? = null,
    val rawUrl: String
)

data class CrossProductUserLinkPayload(
    val clickId: String? = null,
    val sourceProductId: String,
    val targetProductId: String,
    val sourceUid: String? = null,
    val targetUid: String? = null,
    val sourceDeviceId: String? = null,
    val targetDeviceId: String? = null,
    val email: String? = null,
    val placement: String? = null,
    val campaign: String? = null
)
