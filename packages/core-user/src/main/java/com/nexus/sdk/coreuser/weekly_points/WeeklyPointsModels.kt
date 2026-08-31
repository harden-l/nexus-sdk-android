package com.nexus.sdk.coreuser.weekly_points

data class WeeklyPointsInfo(
    val isVip: Boolean,
    val marketProductId: String,
    val weeklyPoints: Int,
    val canClaim: Boolean,
    val cannotClaimReason: String
)

data class WeeklyPointsClaimResult(
    val success: Boolean,
    val points: Int,
    val transactionId: String,
    val claimTime: String
)
