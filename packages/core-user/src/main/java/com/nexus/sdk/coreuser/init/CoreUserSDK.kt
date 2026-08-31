package com.nexus.sdk.coreuser.init

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nexus.sdk.coreuser.coins.ConsumeChatCoinsResult
import com.nexus.sdk.coreuser.email_bind.BindAccountParams
import com.nexus.sdk.coreuser.email_bind.BindAccountResult
import com.nexus.sdk.coreuser.email_bind.BindEmailFlowResult
import com.nexus.sdk.coreuser.email_bind.BindEmailFlowStatus
import com.nexus.sdk.coreuser.email_bind.CoreUserEmailBindDialog
import com.nexus.sdk.coreuser.network.RelatedProduct
import com.nexus.sdk.coreuser.network.RelatedProductApi
import com.nexus.sdk.coreuser.silent_login.CoreUserApi
import com.nexus.sdk.coreuser.silent_login.LoginType
import com.nexus.sdk.coreuser.silent_login.SDKUser
import com.nexus.sdk.coreuser.storage.UserStorage
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsClaimResult
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsInfo

object CoreUserSDK {
    const val VERSION = "0.0.12"

    private lateinit var appContext: Context
    private var config: CoreUserConfig? = null
    private var storage: UserStorage? = null
    private var api: CoreUserApi? = null
    private var relatedProductApi: RelatedProductApi? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun init(context: Context, config: CoreUserConfig) {
        this.appContext = context.applicationContext
        val resolvedConfig = config.copy(version = resolveAppVersion(appContext, config.version))
        this.config = resolvedConfig
        this.storage = UserStorage(appContext)
        this.api = CoreUserApi(resolvedConfig)
        this.relatedProductApi = RelatedProductApi(resolvedConfig)
    }

    fun getSdkConfig(): CoreUserConfig {
        return config ?: throw CoreUserException("CoreUserSDK is not initialized")
    }

    fun fetchSwitchConfig(): String {
        val uid = requireStorage().getUser()?.uid
        return requireApi().getSwitchConfig(
            att = if (isLoginAttributionEnabled()) 1 else 0,
            uid = uid
        ).also { requireStorage().saveSwitchConfig(it) }
    }

    fun fetchSwitchConfigAsync(callback: (CoreUserResult<String>) -> Unit) {
        runAsync("core-user-switch-config", callback) { fetchSwitchConfig() }
    }

    fun getSwitchConfig(): String? = requireStorage().getSwitchConfig()

    fun getApplicationContext(): Context {
        if (!::appContext.isInitialized) throw CoreUserException("CoreUserSDK is not initialized")
        return appContext
    }

    fun getDeviceId(): String {
        return requireStorage().getOrCreateDeviceId()
    }

    fun setLoginAttributionEnabled(enabled: Boolean) {
        requireStorage().setLoginAttributionEnabled(enabled)
    }

    fun isLoginAttributionEnabled(): Boolean {
        return requireStorage().isLoginAttributionEnabled()
    }

    @Throws(CoreUserException::class)
    fun silentLogin(loginType: LoginType = LoginType.GUEST): SDKUser {
        require(loginType == LoginType.GUEST) {
            "silentLogin only supports guest login; use loginWithEmail or loginWithPhone"
        }
        return login(loginType = LoginType.GUEST)
    }

    @Throws(CoreUserException::class)
    fun loginWithEmail(email: String, password: String): SDKUser {
        return login(loginType = LoginType.EMAIL, email = email, password = password)
    }

    fun loginWithEmailAsync(
        email: String,
        password: String,
        callback: (CoreUserResult<SDKUser>) -> Unit
    ) {
        runAsync("core-user-email-login", callback) {
            loginWithEmail(email, password)
        }
    }

    @Throws(CoreUserException::class)
    fun loginWithPhone(phonePrefix: String, phone: String, password: String): SDKUser {
        return login(
            loginType = LoginType.PHONE,
            phonePrefix = phonePrefix,
            phone = phone,
            password = password
        )
    }

    fun loginWithPhoneAsync(
        phonePrefix: String,
        phone: String,
        password: String,
        callback: (CoreUserResult<SDKUser>) -> Unit
    ) {
        runAsync("core-user-phone-login", callback) {
            loginWithPhone(phonePrefix, phone, password)
        }
    }

    private fun login(
        loginType: LoginType,
        email: String? = null,
        phonePrefix: String? = null,
        phone: String? = null,
        password: String? = null
    ): SDKUser {
        val deviceId = getDeviceId()
        val existingUid = requireStorage().getUser()?.uid.orEmpty()
        val loginResult = requireApi().login(
            deviceId = deviceId,
            uid = existingUid,
            loginType = loginType,
            att = if (isLoginAttributionEnabled()) 1 else 0,
            email = email,
            phonePrefix = phonePrefix,
            phone = phone,
            password = password
        )
        val user = runCatching {
            requireApi().getUserInfo(loginResult.uid, deviceId)
        }.getOrElse {
            SDKUser(
                uid = loginResult.uid,
                deviceId = deviceId,
                userInfoSynced = false
            )
        }
        requireStorage().saveUser(user)
        return user
    }

    fun silentLoginAsync(callback: (CoreUserResult<SDKUser>) -> Unit) {
        runAsync("core-user-silent-login", callback) {
            silentLogin()
        }
    }

    fun getCurrentUser(): SDKUser? {
        return requireStorage().getUser()
    }

    @Throws(CoreUserException::class)
    fun getRelatedProducts(): List<RelatedProduct> {
        return requireRelatedProductApi().getRelatedProducts()
    }

    fun getRelatedProductsAsync(callback: (CoreUserResult<List<RelatedProduct>>) -> Unit) {
        runAsync("core-user-related-products", callback) {
            getRelatedProducts()
        }
    }

    @Throws(CoreUserException::class)
    fun fetchUserInfo(): SDKUser {
        val currentUser = getCurrentUser() ?: silentLogin()
        val user = requireApi().getUserInfo(currentUser.uid, currentUser.deviceId)
        requireStorage().saveUser(user)
        return user
    }

    fun fetchUserInfoAsync(callback: (CoreUserResult<SDKUser>) -> Unit) {
        runAsync("core-user-fetch-user", callback) {
            fetchUserInfo()
        }
    }

    @Throws(CoreUserException::class)
    fun getWeeklyPointsInfo(): WeeklyPointsInfo {
        val currentUser = getCurrentUser() ?: silentLogin()
        return requireApi().getWeeklyPointsInfo(currentUser.uid)
    }

    fun getWeeklyPointsInfoAsync(callback: (CoreUserResult<WeeklyPointsInfo>) -> Unit) {
        runAsync("core-user-weekly-points-info", callback) { getWeeklyPointsInfo() }
    }

    @Throws(CoreUserException::class)
    fun claimWeeklyPoints(marketProductId: String? = null): WeeklyPointsClaimResult {
        val currentUser = getCurrentUser() ?: silentLogin()
        return requireApi().claimWeeklyPoints(currentUser.uid, marketProductId)
    }

    fun claimWeeklyPointsAsync(
        marketProductId: String? = null,
        callback: (CoreUserResult<WeeklyPointsClaimResult>) -> Unit
    ) {
        runAsync("core-user-weekly-points-claim", callback) {
            claimWeeklyPoints(marketProductId)
        }
    }

    @Throws(CoreUserException::class)
    fun bindEmail(email: String, password: String): BindAccountResult {
        return bindAccount(
            BindAccountParams(
                accountType = BindAccountParams.AccountType.EMAIL,
                email = email,
                password = password
            )
        )
    }

    fun bindEmailAsync(
        email: String,
        password: String,
        callback: (CoreUserResult<BindAccountResult>) -> Unit
    ) {
        runAsync("core-user-bind-email", callback) {
            bindEmail(email, password)
        }
    }

    @Throws(CoreUserException::class)
    fun bindPhone(phonePrefix: String, phone: String, password: String): BindAccountResult {
        return bindAccount(
            BindAccountParams(
                accountType = BindAccountParams.AccountType.PHONE,
                phonePrefix = phonePrefix,
                phone = phone,
                password = password
            )
        )
    }

    fun bindPhoneAsync(
        phonePrefix: String,
        phone: String,
        password: String,
        callback: (CoreUserResult<BindAccountResult>) -> Unit
    ) {
        runAsync("core-user-bind-phone", callback) {
            bindPhone(phonePrefix, phone, password)
        }
    }

    @Throws(CoreUserException::class)
    fun bindAccount(params: BindAccountParams): BindAccountResult {
        val currentUser = getCurrentUser() ?: silentLogin()
        val result = requireApi().bindAccount(currentUser.uid, currentUser.deviceId, params)
        val updatedUser = when (params.accountType) {
            BindAccountParams.AccountType.EMAIL -> currentUser.copy(
                uid = result.uid,
                email = result.accountValue,
                emailBound = result.bound,
                userInfoSynced = true
            )

            BindAccountParams.AccountType.PHONE -> currentUser.copy(
                uid = result.uid,
                phone = result.accountValue,
                phoneBound = result.bound,
                userInfoSynced = true   
            )
        }
        requireStorage().saveUser(updatedUser)
        return result
    }

    fun bindAccountAsync(
        params: BindAccountParams,
        callback: (CoreUserResult<BindAccountResult>) -> Unit
    ) {
        runAsync("core-user-bind-account", callback) {
            bindAccount(params)
        }
    }

    @Throws(CoreUserException::class)
    fun consumeChatCoins(cost: Double, remark: String? = null): ConsumeChatCoinsResult {
        require(cost > 0) { "cost must be greater than 0" }
        val currentUser = getCurrentUser() ?: silentLogin()
        return requireApi().consumeChatCoins(currentUser.uid, cost, remark)
    }

    fun consumeChatCoinsAsync(
        cost: Double,
        remark: String? = null,
        callback: (CoreUserResult<ConsumeChatCoinsResult>) -> Unit
    ) {
        runAsync("core-user-consume-chat-coins", callback) {
            consumeChatCoins(cost, remark)
        }
    }

    fun ensureEmailBound(
        activity: Activity,
        callback: (BindEmailFlowResult) -> Unit = {}
    ) {
        Thread({
            val user = try {
                fetchUserInfo()
            } catch (error: Throwable) {
                postResult(
                    callback,
                    BindEmailFlowResult(BindEmailFlowStatus.USER_INFO_FAILED, error = error)
                )
                return@Thread
            }

            if (user.emailBound) {
                postResult(
                    callback,
                    BindEmailFlowResult(BindEmailFlowStatus.ALREADY_BOUND, user = user)
                )
                return@Thread
            }

            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) {
                    callback(BindEmailFlowResult(BindEmailFlowStatus.CANCELLED, user = user))
                    return@post
                }
                CoreUserEmailBindDialog.show(
                    activity = activity,
                    initialEmail = user.email,
                    onCancel = {
                        callback(BindEmailFlowResult(BindEmailFlowStatus.CANCELLED, user = user))
                    },
                    onSubmit = { email, password ->
                        bindEmailFromDialog(user, email, password, callback)
                    }
                )
            }
        }, "core-user-ensure-email-bound").start()
    }

    @Throws(CoreUserException::class)
    fun logout() {
        val currentUser = getCurrentUser()
        if (!currentUser?.uid.isNullOrBlank()) {
            requireApi().logout(currentUser.uid)
        }
        clearLocalSession()
    }

    fun logoutAsync(callback: (CoreUserResult<Unit>) -> Unit) {
        runAsync("core-user-logout", callback) {
            logout()
        }
    }

    fun clearLocalSession() {
        requireStorage().clearUser()
        requireStorage().clearSwitchConfig()
    }

    private fun requireStorage(): UserStorage {
        return storage ?: throw CoreUserException("CoreUserSDK is not initialized")
    }

    private fun requireApi(): CoreUserApi {
        return api ?: throw CoreUserException("CoreUserSDK is not initialized")
    }

    private fun requireRelatedProductApi(): RelatedProductApi {
        return relatedProductApi ?: throw CoreUserException("CoreUserSDK is not initialized")
    }

    private fun resolveAppVersion(context: Context, fallback: String): String {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }

    private fun bindEmailFromDialog(
        user: SDKUser,
        email: String,
        password: String,
        callback: (BindEmailFlowResult) -> Unit
    ) {
        Thread({
            try {
                val result = bindEmail(email, password)
                postResult(
                    callback,
                    BindEmailFlowResult(
                        status = BindEmailFlowStatus.BOUND,
                        user = getCurrentUser() ?: user.copy(
                            email = email,
                            emailBound = result.bound,
                            userInfoSynced = true
                        ),
                        bindResult = result
                    )
                )
            } catch (error: Throwable) {
                postResult(
                    callback,
                    BindEmailFlowResult(BindEmailFlowStatus.BIND_FAILED, user = user, error = error)
                )
            }
        }, "core-user-bind-email").start()
    }

    private fun postResult(
        callback: (BindEmailFlowResult) -> Unit,
        result: BindEmailFlowResult
    ) {
        mainHandler.post { callback(result) }
    }

    private fun <T> runAsync(
        threadName: String,
        callback: (CoreUserResult<T>) -> Unit,
        block: () -> T
    ) {
        Thread({
            val result = try {
                CoreUserResult(value = block())
            } catch (error: Throwable) {
                CoreUserResult<T>(error = error)
            }
            mainHandler.post { callback(result) }
        }, threadName).start()
    }
}
