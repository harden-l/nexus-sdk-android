package com.nexus.sdk.payment.order_verify

import com.nexus.sdk.payment.config.PaymentChannel
enum class OrderStatus(val code: Int) {
    PENDING(10),
    SUCCESS(20),
    FAILED(60),
    CANCELLED(70),
    REFUNDED(80),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int?): OrderStatus {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

data class OrderVerificationRequest(
    val channel: PaymentChannel,
    val token: String,
    val platformProductId: String,
    val uid: String,
    val isSubscription: Boolean,
    val tradeOrderId: String
)

data class OrderVerificationResult(
    val tradeOrderId: String,
    val status: OrderStatus,
    val isSubscription: Boolean,
    val startedTime: Long? = null,
    val endsTime: Long? = null,
    val successCount: Int? = null,
    val token: String? = null,
    val platformProductId: String? = null,
    val amount: String? = null
) {
    val isSuccessful: Boolean = status == OrderStatus.SUCCESS
}