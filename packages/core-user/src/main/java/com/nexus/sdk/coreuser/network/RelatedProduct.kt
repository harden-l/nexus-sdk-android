package com.nexus.sdk.coreuser.network

data class RelatedProduct(
    val productId: String,
    val productName: String,
    val description: String = "",
    val icon: String = "",
    val downloadUrl: String = "",
    val packageName: String = ""
)
