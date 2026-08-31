package com.nexus.sdk.coreuser.email_bind

data class BindAccountResult(
    val uid: String,
    val accountType: String,
    val accountValue: String,
    val bound: Boolean
)
