package com.nexus.sdk.coreuser.init

data class CoreUserResult<T>(
    val value: T? = null,
    val error: Throwable? = null
) {
    val isSuccess: Boolean get() = error == null
    val isFailure: Boolean get() = error != null

    fun getOrNull(): T? = value

    fun exceptionOrNull(): Throwable? = error
}
