package com.nexus.sdk.growth.purchase_revenue

enum class PaymentChannel(val wireValue: String) {
    APP_STORE("app_store"),
    GOOGLE_PLAY("google_play"),
    STRIPE("stripe"),
    PAYPAL("paypal"),
    WEB_CHECKOUT("web_checkout")
}
