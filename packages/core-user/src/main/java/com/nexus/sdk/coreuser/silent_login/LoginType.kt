package com.nexus.sdk.coreuser.silent_login

enum class LoginType(val wireValue: String) {
    GUEST("guest"),
    EMAIL("email"),
    PHONE("phone")
}
