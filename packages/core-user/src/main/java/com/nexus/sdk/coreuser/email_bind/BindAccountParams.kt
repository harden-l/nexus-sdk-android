package com.nexus.sdk.coreuser.email_bind

data class BindAccountParams(
    val accountType: AccountType,
    val email: String? = null,
    val phonePrefix: String? = null,
    val phone: String? = null,
    val password: String
) {
    enum class AccountType(val wireValue: String) {
        EMAIL("email"),
        PHONE("phone")
    }
}
