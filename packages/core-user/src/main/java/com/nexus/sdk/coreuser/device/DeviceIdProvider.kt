package com.nexus.sdk.coreuser.device

import android.content.Context
import android.provider.Settings
import java.util.Locale
import java.util.UUID

internal class DeviceIdProvider(
    private val context: Context
) {
    fun resolveDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.lowercase(Locale.US)

        if (isUsableAndroidId(androidId)) {
            return androidId.orEmpty()
        }

        return UUID.randomUUID().toString()
    }

    private fun isUsableAndroidId(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.length >= 8
    }
}
