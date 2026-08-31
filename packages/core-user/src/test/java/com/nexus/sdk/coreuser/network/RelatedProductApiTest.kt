package com.nexus.sdk.coreuser.network

import com.nexus.sdk.coreuser.init.CoreUserConfig
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedProductApiTest {
    @Test
    fun getRelatedProductsSendsAccountNameAndProductId() {
        val server = CapturingHttpServer(
            """{"code":1,"message":"success","data":{"list":[{"product_id":8,"product_name":"Related App"}]}}"""
        )
        server.start()

        try {
            val config = CoreUserConfig(
                productId = "7",
                productName = "TEST PRODUCT",
                accountName = "google-play-account",
                apiBaseUrl = "http://127.0.0.1:${server.port}",
                encrypt = false
            )

            val products = RelatedProductApi(config).getRelatedProducts()

            assertEquals("/related_products", server.requestPath())
            assertTrue(server.requestBody().contains("\"product_id\":\"7\""))
            assertTrue(server.requestBody().contains("\"account_name\":\"google-play-account\""))
            assertEquals(1, products.size)
            assertEquals("8", products.single().productId)
        } finally {
            server.stop()
        }
    }

    private class CapturingHttpServer(private val response: String) {
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
                            if (headerBytes.size >= 4 &&
                                headerBytes.takeLast(4).toByteArray().contentEquals("\r\n\r\n".toByteArray())
                            ) break
                        }
                        val headers = headerBytes.toByteArray().toString(Charsets.UTF_8)
                        requestPath = headers.lineSequence().firstOrNull()?.split(" ")?.getOrNull(1).orEmpty()
                        val contentLength = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                            .find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                        val bodyBytes = ByteArray(contentLength)
                        var offset = 0
                        while (offset < contentLength) {
                            val read = input.read(bodyBytes, offset, contentLength - offset)
                            if (read == -1) break
                            offset += read
                        }
                        requestBody = bodyBytes.toString(Charsets.UTF_8)
                        client.getOutputStream().apply {
                            write(
                                ("HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${response.toByteArray().size}\r\n" +
                                    "Connection: close\r\n\r\n" + response).toByteArray()
                            )
                            flush()
                        }
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
            if (::thread.isInitialized) thread.join(5_000)
        }
    }
}
