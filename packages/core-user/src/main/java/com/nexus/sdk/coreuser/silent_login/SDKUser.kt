package com.nexus.sdk.coreuser.silent_login

data class SDKUser(
    val uid: String,
    val deviceId: String,
    val email: String? = null,
    val phone: String? = null,
    val emailBound: Boolean = false,
    val phoneBound: Boolean = false,
    val balance: Double = 0.0,
    val isVip: Boolean = false,
    val vipExpiredAt: Long = 0L,
    val userInfoSynced: Boolean = false
)
