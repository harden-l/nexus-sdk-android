package com.nexus.sdk.coreuser.storage

import android.content.Context
import com.nexus.sdk.coreuser.device.DeviceIdProvider
import com.nexus.sdk.coreuser.silent_login.SDKUser

internal class UserStorage(context: Context) {
    private val preferences = context.getSharedPreferences("gexin_core_user", Context.MODE_PRIVATE)
    private val deviceIdProvider = DeviceIdProvider(context.applicationContext)

    fun getOrCreateDeviceId(): String {
        val current = preferences.getString(KEY_DEVICE_ID, null)
        if (!current.isNullOrBlank()) return current

        val generated = deviceIdProvider.resolveDeviceId()
        preferences.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun saveUser(user: SDKUser) {
        preferences.edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_PHONE, user.phone)
            .putBoolean(KEY_EMAIL_BOUND, user.emailBound)
            .putBoolean(KEY_PHONE_BOUND, user.phoneBound)
            .putString(KEY_BALANCE, user.balance.toString())
            .putBoolean(KEY_USER_INFO_SYNCED, user.userInfoSynced)
            .apply()
    }

    fun getUser(): SDKUser? {
        val uid = preferences.getString(KEY_UID, null)?.takeIf { it.isNotBlank() } ?: return null
        return SDKUser(
            uid = uid,
            deviceId = getOrCreateDeviceId(),
            email = preferences.getString(KEY_EMAIL, null),
            phone = preferences.getString(KEY_PHONE, null),
            emailBound = preferences.getBoolean(KEY_EMAIL_BOUND, false),
            phoneBound = preferences.getBoolean(KEY_PHONE_BOUND, false),
            balance = storedBalance(),
            userInfoSynced = preferences.getBoolean(KEY_USER_INFO_SYNCED, false)
        )
    }

    fun saveSwitchConfig(config: String) {
        preferences.edit().putString(KEY_SWITCH_CONFIG, config).apply()
    }

    fun getSwitchConfig(): String? = preferences.getString(KEY_SWITCH_CONFIG, null)

    fun clearSwitchConfig() {
        preferences.edit().remove(KEY_SWITCH_CONFIG).apply()
    }

    fun setLoginAttributionEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_LOGIN_ATTRIBUTION_ENABLED, enabled)
            .apply()
    }

    fun isLoginAttributionEnabled(): Boolean {
        return preferences.getBoolean(KEY_LOGIN_ATTRIBUTION_ENABLED, false)
    }

    fun clearUser() {
        preferences.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PHONE)
            .remove(KEY_EMAIL_BOUND)
            .remove(KEY_PHONE_BOUND)
            .remove(KEY_BALANCE)
            .remove(KEY_USER_INFO_SYNCED)
            .apply()
    }

    private fun storedBalance(): Double {
        return when (val value = preferences.all[KEY_BALANCE]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: 0.0
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_UID = "uid"
        private const val KEY_SWITCH_CONFIG = "switch_config"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_EMAIL_BOUND = "email_bound"
        private const val KEY_PHONE_BOUND = "phone_bound"
        private const val KEY_BALANCE = "balance"
        private const val KEY_USER_INFO_SYNCED = "user_info_synced"
        private const val KEY_LOGIN_ATTRIBUTION_ENABLED = "login_attribution_enabled"
    }
}
