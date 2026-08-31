package com.nexus.sdk.payment.subscription

class PaymentException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
