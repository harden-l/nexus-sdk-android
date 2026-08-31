package com.nexus.sdk.coreuser.coins

data class ConsumeChatCoinsResult(
    val uid: String,
    val changeType: String,
    val cost: Double,
    val beforeCoins: Double,
    val afterCoins: Double,
    val balance: Double
)
