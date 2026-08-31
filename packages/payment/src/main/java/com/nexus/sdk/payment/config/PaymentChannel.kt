package com.nexus.sdk.payment.config

enum class PaymentChannel(val wireValue: String) {
    GOOGLE_PLAY("google_play"),
    APP_STORE("app_store"),
    STRIPE("stripe"),
    PAYPAL("paypal"),
    WEB_CHECKOUT("web_checkout"),
    MOCK("mock")
}
