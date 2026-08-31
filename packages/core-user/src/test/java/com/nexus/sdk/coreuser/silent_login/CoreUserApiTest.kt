package com.nexus.sdk.coreuser.silent_login

import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserException
import java.net.ServerSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUserApiTest {
    @Test
    fun switchConfigReturnsRawResponseFromRootEndpoint() {
        val rawConfig = "{\"status\":1,\"feature\":{\"enabled\":true,\"ratio\":0.5}}"
        val server = CapturingHttpServer(rawConfig)
        server.start()
        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )
            val result = CoreUserApi(config).getSwitchConfig(att = 0, uid = "u1")
            assertEquals("/", server.requestPath())
            assertEquals(rawConfig, result)
            assertBodyContains(server.requestBody(), "\"version\":\"1.0.0\"")
            assertBodyContains(server.requestBody(), "\"uid\":\"u1\"")
        } finally {
            server.stop()
        }
    }

    @Test
    fun getUserInfoScalesDecimalBalanceForBusinessUsage() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"uid":"u1","balance":88.75,"is_vip":true,"vip_expired_at":1780000000}}"""
        )
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            val user = CoreUserApi(config).getUserInfo(uid = "u1", deviceId = "device-1")

            assertEquals(8_875.0, user.balance, 0.0)
            assertTrue(user.isVip)
            assertEquals(1780000000L, user.vipExpiredAt)
        } finally {
            server.stop()
        }
    }

    @Test
    fun weeklyPointsInfoUsesDedicatedEndpointAndParsesClaimState() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"is_vip":true,"market_product_id":"subscription.monthly","weekly_points":30,"can_claim":true,"cannot_claim_reason":""}}"""
        )
        server.start()
        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )
            val info = CoreUserApi(config).getWeeklyPointsInfo("u1")
            assertEquals("/m/weekly_points/info", server.requestPath())
            assertTrue(info.isVip)
            assertEquals("subscription.monthly", info.marketProductId)
            assertEquals(30, info.weeklyPoints)
            assertTrue(info.canClaim)
        } finally {
            server.stop()
        }
    }

    @Test
    fun weeklyPointsClaimSendsOptionalSkuAndParsesResult() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"success":true,"points":30,"transaction_id":"101","claim_time":"2026-08-26T09:30:00Z"}}"""
        )
        server.start()
        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )
            val result = CoreUserApi(config).claimWeeklyPoints("u1", "subscription.monthly")
            assertEquals("/m/weekly_points/claim", server.requestPath())
            assertTrue(result.success)
            assertEquals(30, result.points)
            assertEquals("101", result.transactionId)
            assertBodyContains(server.requestBody(), "\"market_product_id\":\"subscription.monthly\"")
        } finally {
            server.stop()
        }
    }

    @Test
    fun loginSendsGrantTierCodeWhenConfigured() {
        val server = CapturingHttpServer("""{"code":1,"message":"success","data":{"uid":"u1"}}""")
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false,
                gt = 2
            )

            val result = CoreUserApi(config).login(
                deviceId = "device-1",
                uid = "",
                loginType = LoginType.GUEST,
                att = 0
            )

            assertEquals("u1", result.uid)
            assertEquals("/m/v7/user/login", server.requestPath())
            val grantTierCode = Regex(""""gt"\s*:\s*(\d+)""")
                .find(server.requestBody())
                ?.groupValues
                ?.get(1)
                ?.toInt()
            assertEquals(2, grantTierCode)
        } finally {
            server.stop()
        }
    }

    @Test
    fun emailLoginSendsEmailAndPassword() {
        val server = CapturingHttpServer("""{"code":1,"message":"success","data":{"uid":"email-user"}}""")
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            CoreUserApi(config).login(
                deviceId = "device-1",
                uid = "",
                loginType = LoginType.EMAIL,
                att = 0,
                email = " user@example.com ",
                password = "secret123"
            )

            assertBodyContains(server.requestBody(), "\"login_type\":\"email\"")
            assertBodyContains(server.requestBody(), "\"email\":\"user@example.com\"")
            assertBodyContains(server.requestBody(), "\"password\":\"secret123\"")
        } finally {
            server.stop()
        }
    }

    @Test
    fun emailLoginRejectsMissingPassword() {
        val config = CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "http://127.0.0.1:1",
            encrypt = false
        )

        assertThrows(IllegalArgumentException::class.java) {
            CoreUserApi(config).login(
                deviceId = "device-1",
                uid = "",
                loginType = LoginType.EMAIL,
                att = 0,
                email = "user@example.com",
                password = ""
            )
        }
    }

    @Test
    fun bindEmailSendsPassword() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"uid":"u1","account_type":"email","account_value":"user@example.com","bound":true}}"""
        )
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            CoreUserApi(config).bindAccount(
                uid = "u1",
                deviceId = "device-1",
                params = com.nexus.sdk.coreuser.email_bind.BindAccountParams(
                    accountType = com.nexus.sdk.coreuser.email_bind.BindAccountParams.AccountType.EMAIL,
                    email = "user@example.com",
                    password = "secret123"
                )
            )

            assertEquals("/m/v7/user/bind_account", server.requestPath())
            assertBodyContains(server.requestBody(), "\"password\":\"secret123\"")
        } finally {
            server.stop()
        }
    }

    @Test
    fun consumeChatCoinsSendsRequestAndParsesResult() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"uid":"u1","change_type":"consume_chat","cost":2.5,"before_coins":120.5,"after_coins":118,"balance":118}}"""
        )
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            val result = CoreUserApi(config).consumeChatCoins(
                uid = "u1",
                cost = 2.5,
                remark = "chat billing"
            )

            assertEquals("/m/v7/coins/consume_chat", server.requestPath())
            assertEquals("u1", result.uid)
            assertEquals("consume_chat", result.changeType)
            assertEquals(2.5, result.cost, 0.0)
            assertEquals(120.5, result.beforeCoins, 0.0)
            assertEquals(118.0, result.afterCoins, 0.0)
            assertEquals(118.0, result.balance, 0.0)
            assertBodyContains(server.requestBody(), """"uid":"u1"""")
            assertBodyContains(server.requestBody(), """"cost":2.5""")
            assertBodyContains(server.requestBody(), """"remark":"chat billing"""")
        } finally {
            server.stop()
        }
    }

    @Test
    fun consumeChatCoinsSkipsBlankRemark() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"uid":"u1","change_type":"consume_chat","cost":1,"before_coins":2,"after_coins":1,"balance":1}}"""
        )
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            CoreUserApi(config).consumeChatCoins(uid = "u1", cost = 1.0, remark = " ")

            assertFalse(server.requestBody().contains("remark"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun consumeChatCoinsThrowsApiError() {
        val server = CapturingHttpServer("""{"code":0,"message":"not enough coins","data":{}}""")
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            val error = assertThrows(CoreUserException::class.java) {
                CoreUserApi(config).consumeChatCoins(uid = "u1", cost = 1.0, remark = null)
            }
            assertEquals("not enough coins", error.message)
        } finally {
            server.stop()
        }
    }

    @Test
    fun consumeChatCoinsRejectsInvalidCost() {
        val config = CoreUserConfig(
            productId = "7",
            productName = "TEST PRODUCT",
            accountName = "test-account",
            apiBaseUrl = "http://127.0.0.1:1",
            encrypt = false
        )

        assertThrows(IllegalArgumentException::class.java) {
            CoreUserApi(config).consumeChatCoins(uid = "u1", cost = 0.0, remark = null)
        }
    }

    @Test
    fun logoutSendsDeregisterRequest() {
        val server = CapturingHttpServer("""{"code":1,"message":"success","data":{}}""")
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            CoreUserApi(config).logout(uid = "u1")

            assertEquals("/m/v7/deregister", server.requestPath())
            assertBodyContains(server.requestBody(), """"uid":"u1"""")
        } finally {
            server.stop()
        }
    }

    @Test
    fun logoutThrowsApiError() {
        val server = CapturingHttpServer("""{"code":0,"message":"logout denied","data":{}}""")
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "test-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            val error = assertThrows(CoreUserException::class.java) {
                CoreUserApi(config).logout(uid = "u1")
            }
            assertEquals("logout denied", error.message)
        } finally {
            server.stop()
        }
    }

    private fun assertBodyContains(body: String, value: String) {
        assertTrue("Expected body to contain $value but was $body", body.contains(value))
    }

    private class CapturingHttpServer(
        private val response: String
    ) {
        private val socket = ServerSocket(0)
        private var requestBody = ""
        private var requestPath = ""
        private lateinit var thread: Thread

        val port: Int = socket.localPort

        fun start() {
            thread = Thread {
                socket.use { serverSocket ->
                    serverSocket.accept().use { client ->
                        val input = client.getInputStream()
                        val headerBytes = mutableListOf<Byte>()
                        while (true) {
                            val next = input.read()
                            if (next == -1) break
                            headerBytes += next.toByte()
                            if (headerBytes.endsWithHeaderTerminator()) break
                        }
                        val headers = headerBytes.toByteArray().toString(Charsets.UTF_8)
                        requestPath = headers.lineSequence().firstOrNull()
                            ?.split(" ")
                            ?.getOrNull(1)
                            .orEmpty()
                        val contentLength = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                            .find(headers)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                            ?: 0
                        val bodyBytes = ByteArray(contentLength)
                        var offset = 0
                        while (offset < contentLength) {
                            val read = input.read(bodyBytes, offset, contentLength - offset)
                            if (read == -1) break
                            offset += read
                        }
                        requestBody = bodyBytes.toString(Charsets.UTF_8)
                        val output = client.getOutputStream()
                        output.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${response.toByteArray(Charsets.UTF_8).size}\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n" +
                                    response
                                ).toByteArray(Charsets.UTF_8)
                        )
                        output.flush()
                    }
                }
            }
            thread.start()
        }

        fun requestBody(): String {
            thread.join(5_000)
            return requestBody
        }

        fun requestPath(): String {
            thread.join(5_000)
            return requestPath
        }

        fun stop() {
            runCatching { socket.close() }
        }

        private fun MutableList<Byte>.endsWithHeaderTerminator(): Boolean {
            if (size < 4) return false
            return this[size - 4] == '\r'.code.toByte() &&
                this[size - 3] == '\n'.code.toByte() &&
                this[size - 2] == '\r'.code.toByte() &&
                this[size - 1] == '\n'.code.toByte()
        }
    }
}
