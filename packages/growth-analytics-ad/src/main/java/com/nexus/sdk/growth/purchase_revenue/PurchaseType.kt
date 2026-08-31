package com.nexus.sdk.growth.purchase_revenue

enum class PurchaseType(val wireValue: String) {
    SUBSCRIPTION("subscription"),
    ONE_TIME("one_time"),
    THIRD_PARTY("third_party")
}
