package com.nexus.sdk.coreuser.email_bind

import com.nexus.sdk.coreuser.silent_login.SDKUser

data class BindEmailFlowResult(
    val status: BindEmailFlowStatus,
    val user: SDKUser? = null,
    val bindResult: BindAccountResult? = null,
    val error: Throwable? = null
)

enum class BindEmailFlowStatus {
    ALREADY_BOUND,
    BOUND,
    CANCELLED,
    USER_INFO_FAILED,
    BIND_FAILED
}
