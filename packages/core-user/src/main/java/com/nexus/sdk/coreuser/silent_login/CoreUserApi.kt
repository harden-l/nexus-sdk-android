package com.nexus.sdk.coreuser.silent_login

import android.util.Log
import com.nexus.sdk.coreuser.coins.ConsumeChatCoinsResult
import com.nexus.sdk.coreuser.email_bind.BindAccountParams
import com.nexus.sdk.coreuser.email_bind.BindAccountResult
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserException
import com.nexus.sdk.coreuser.network.ApiRequestEncryption
import com.nexus.sdk.coreuser.network.SimpleJson
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsClaimResult
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsInfo
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CoreUserApi(
    private val config: CoreUserConfig
) {
    fun login(
        deviceId: String,
        uid: String,
        loginType: LoginType,
        att: Int,
        email: String? = null,
        phonePrefix: String? = null,
        phone: String? = null,
        password: String? = null
    ): LoginResult {
        validateLoginParams(loginType, email, phonePrefix, phone, password)
        val body = mutableMapOf<String, Any?>(
            "login_type" to loginType.wireValue,
            "uid" to uid,
            "device_id" to deviceId,
            "version" to config.version,
            "country" to config.country,
            "language" to config.language,
            "is_has_sim" to false,
            "st" to timestamp(),
            "att" to att,
            "level" to 1,
            "email" to email?.trim().orEmpty(),
            "phone_prefix" to phonePrefix?.trim().orEmpty(),
            "phone" to phone?.trim().orEmpty(),
            "password" to password.orEmpty(),
        ).apply {
            config.gt?.let { put("gt", it) }
        }
        val response = post("/m/v7/user/login", body, encrypt = false)
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Login failed"
            throw CoreUserException(message)
        }
        val uid = SimpleJson.findRootString(response, "uid")
            ?: SimpleJson.findDataString(response, "uid")
            ?: SimpleJson.findString(response, "uid")
            ?: throw CoreUserException(
                SimpleJson.findRootString(response, "message")
                    ?: SimpleJson.findString(response, "message")
                    ?: "Login response missing uid"
            )
        return LoginResult(uid = uid)
    }

    fun getSwitchConfig(att: Int, uid: String?): String {
        val body = mapOf(
            "version" to config.version,
            "language" to config.language,
            "country" to config.country,
            "st" to timestamp(),
            "is_has_sim" to false,
            "att" to att,
            "level" to 1,
            "uid" to uid.orEmpty()
        )
        return post("/", body, encrypt = false)
    }

    fun getUserInfo(uid: String, deviceId: String): SDKUser {
        val response = post("/m/v7/user/info", mapOf("uid" to uid))
        return parseUserInfo(response, uid, deviceId)
    }

    fun getWeeklyPointsInfo(uid: String): WeeklyPointsInfo {
        val response = post("/m/weekly_points/info", mapOf("uid" to uid))
        ensureSuccess(response, "Get weekly points info failed")
        val data = SimpleJson.findDataObjectMap(response).ifEmpty { SimpleJson.objectToMap(response) }
        return WeeklyPointsInfo(
            isVip = data["is_vip"] as? Boolean ?: false,
            marketProductId = data["market_product_id"]?.toString().orEmpty(),
            weeklyPoints = data["weekly_points"].toIntOrDefault(0),
            canClaim = data["can_claim"] as? Boolean ?: false,
            cannotClaimReason = data["cannot_claim_reason"]?.toString().orEmpty()
        )
    }

    fun claimWeeklyPoints(uid: String, marketProductId: String? = null): WeeklyPointsClaimResult {
        val body = mutableMapOf<String, Any?>("uid" to uid).apply {
            marketProductId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("market_product_id", it) }
        }
        val response = post("/m/weekly_points/claim", body)
        ensureSuccess(response, "Claim weekly points failed")
        val data = SimpleJson.findDataObjectMap(response).ifEmpty { SimpleJson.objectToMap(response) }
        return WeeklyPointsClaimResult(
            success = data["success"] as? Boolean ?: false,
            points = data["points"].toIntOrDefault(0),
            transactionId = data["transaction_id"]?.toString().orEmpty(),
            claimTime = data["claim_time"]?.toString().orEmpty()
        )
    }

    fun bindAccount(uid: String, deviceId: String, params: BindAccountParams): BindAccountResult {
        validateBindParams(params)
        val body = mapOf(
            "uid" to uid,
            "device_id" to deviceId,
            "account_type" to params.accountType.wireValue,
            "email" to params.email?.trim().orEmpty(),
            "phone_prefix" to params.phonePrefix?.trim().orEmpty(),
            "phone" to params.phone?.trim().orEmpty(),
            "password" to params.password
        )
        val response = post("/m/v7/user/bind_account", body)
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Bind account failed"
            throw CoreUserException(message)
        }

        return BindAccountResult(
            uid = SimpleJson.findDataString(response, "uid")
                ?: SimpleJson.findRootString(response, "uid")
                ?: uid,
            accountType = SimpleJson.findDataString(response, "account_type")
                ?: SimpleJson.findRootString(response, "account_type")
                ?: params.accountType.wireValue,
            accountValue = SimpleJson.findDataString(response, "account_value")
                ?: SimpleJson.findRootString(response, "account_value")
                ?: params.email
                ?: listOfNotNull(params.phonePrefix, params.phone).joinToString(""),
            bound = SimpleJson.findDataBoolean(response, "bound")
                ?: SimpleJson.findBoolean(response, "bound")
                ?: true
        )
    }

    fun consumeChatCoins(uid: String, cost: Double, remark: String?): ConsumeChatCoinsResult {
        require(cost > 0) { "cost must be greater than 0" }
        val body = mutableMapOf<String, Any?>(
            "uid" to uid,
            "cost" to cost
        ).apply {
            remark?.trim()?.takeIf { it.isNotEmpty() }?.let { put("remark", it) }
        }
        val response = post("/m/v7/coins/consume_chat", body)
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Consume chat coins failed"
            throw CoreUserException(message)
        }
        val data = SimpleJson.findDataObjectMap(response).ifEmpty {
            SimpleJson.objectToMap(response)
        }
        return ConsumeChatCoinsResult(
            uid = data["uid"]?.toString()?.takeIf { it.isNotBlank() } ?: uid,
            changeType = data["change_type"]?.toString().orEmpty(),
            cost = data["cost"].toDoubleOrDefault(cost),
            beforeCoins = data["before_coins"].toDoubleOrDefault(0.0),
            afterCoins = data["after_coins"].toDoubleOrDefault(0.0),
            balance = data["balance"].toDoubleOrDefault(0.0)
        )
    }

    fun logout(uid: String) {
        val response = post("/m/v7/deregister", mapOf("uid" to uid))
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Logout failed"
            throw CoreUserException(message)
        }
    }

    private fun post(
        path: String,
        values: Map<String, Any?>,
        encrypt: Boolean = config.encrypt
    ): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(config.apiBaseUrl.trimEnd('/') + path)
            if (config.debug) {
                Log.d(TAG, "POST url:$url")
            }
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Product", config.productName)
                setRequestProperty("Encrypt", if (encrypt) "1" else "0")
                if (!encrypt) {
                    setRequestProperty("ProductId", config.productId)
                }
            }

            val plainBody = SimpleJson.stringify(values)
            val requestBody = ApiRequestEncryption.prepareBody(
                body = plainBody,
                config = config,
                encrypt = encrypt
            )
            if (config.debug) {
                val debugValues = values.toMutableMap().apply {
                    if (containsKey("password")) put("password", "***")
                }
                Log.d(
                    TAG,
                    "POST $path encrypt=${if (encrypt) "1" else "0"} request=${SimpleJson.stringify(debugValues)}"
                )
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response =
                stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            val decodedResponse = ApiRequestEncryption.readResponse(
                response = response,
                config = config,
                encrypt = encrypt
            )
            if (config.debug) {
                Log.d(TAG, "POST $path status=$status response=$decodedResponse")
            }

            if (status !in 200..299) {
                throw CoreUserException("HTTP $status: $decodedResponse")
            }
            return decodedResponse
        } catch (error: Throwable) {
            if (config.debug) {
                Log.e(TAG, "POST $path failed: ${error.javaClass.simpleName}: ${error.message}", error)
            }
            throw if (error is CoreUserException) {
                error
            } else {
                CoreUserException(
                    "POST $path failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                    error
                )
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseUserInfo(response: String, fallbackUid: String, deviceId: String): SDKUser {
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Get user info failed"
            throw CoreUserException(message)
        }
        val data = SimpleJson.findDataObjectMap(response).ifEmpty {
            SimpleJson.objectToMap(response)
        }
        return SDKUser(
            uid = data["uid"]?.toString()?.takeIf { it.isNotBlank() } ?: fallbackUid,
            deviceId = deviceId,
            email = data["email"].toNullableString(),
            phone = data["phone"].toNullableString(),
            emailBound = data["email_bound"] as? Boolean ?: false,
            phoneBound = data["phone_bound"] as? Boolean ?: false,
            balance = data["balance"].toDoubleOrDefault(0.0) * USER_BALANCE_DISPLAY_SCALE,
            isVip = data["is_vip"] as? Boolean ?: false,
            vipExpiredAt = data["vip_expired_at"].toLongOrDefault(0L),
            userInfoSynced = true
        )
    }

    private fun Any?.toNullableString(): String? {
        return this?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun Any?.toDoubleOrDefault(defaultValue: Double): Double {
        return when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        } ?: defaultValue
    }

    private fun Any?.toIntOrDefault(defaultValue: Int): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    } ?: defaultValue

    private fun Any?.toLongOrDefault(defaultValue: Long): Long = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    } ?: defaultValue

    private fun ensureSuccess(response: String, fallbackMessage: String) {
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            throw CoreUserException(
                SimpleJson.findRootString(response, "message")
                    ?: SimpleJson.findString(response, "message")
                    ?: fallbackMessage
            )
        }
    }

    private fun validateBindParams(params: BindAccountParams) {
        require(params.password.isNotBlank()) { "password is required" }
        when (params.accountType) {
            BindAccountParams.AccountType.EMAIL -> {
                require(!params.email.isNullOrBlank()) { "email is required" }
            }

            BindAccountParams.AccountType.PHONE -> {
                require(!params.phonePrefix.isNullOrBlank()) { "phonePrefix is required" }
                require(!params.phone.isNullOrBlank()) { "phone is required" }
            }
        }
    }

    private fun validateLoginParams(
        loginType: LoginType,
        email: String?,
        phonePrefix: String?,
        phone: String?,
        password: String?
    ) {
        when (loginType) {
            LoginType.GUEST -> Unit
            LoginType.EMAIL -> {
                require(!email.isNullOrBlank()) { "email is required" }
                require(!password.isNullOrBlank()) { "password is required" }
            }
            LoginType.PHONE -> {
                require(!phonePrefix.isNullOrBlank() && !phone.isNullOrBlank()) {
                    "phonePrefix and phone are required"
                }
                require(!password.isNullOrBlank()) { "password is required" }
            }
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
    }

    companion object {
        private const val TAG = "CoreUserApi"
        private const val USER_BALANCE_DISPLAY_SCALE = 100.0
    }
}
