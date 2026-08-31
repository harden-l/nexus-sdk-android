package com.nexus.sdk.payment.order_verify

import com.nexus.sdk.payment.subscription.PaymentException
import com.nexus.sdk.payment.config.SimpleJson
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.coreuser.network.ApiRequestEncryption
import com.nexus.sdk.coreuser.init.CoreUserConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal class OrderVerificationApi(
    private val coreConfig: CoreUserConfig
) {
    fun verify(request: OrderVerificationRequest): OrderVerificationResult {
        val path = when (request.channel) {
            PaymentChannel.GOOGLE_PLAY -> "/pp/v7/gp/os"
            PaymentChannel.APP_STORE -> "/pp/v7/apple/os"
            else -> throw PaymentException("Server verification is not configured for ${request.channel.wireValue}")
        }
        val response = post(path, request.toJson())
        val code = SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            throw PaymentException(SimpleJson.findString(response, "message") ?: "Order verification failed")
        }
        return OrderVerificationResult(
            tradeOrderId = SimpleJson.findString(response, "trade_order_id") ?: request.tradeOrderId,
            status = OrderStatus.fromCode(SimpleJson.findInt(response, "status")),
            isSubscription = SimpleJson.findBoolean(response, "issub") ?: request.isSubscription,
            startedTime = SimpleJson.findLong(response, "started_time"),
            endsTime = SimpleJson.findLong(response, "ends_time"),
            successCount = SimpleJson.findInt(response, "success_count"),
            token = SimpleJson.findString(response, "token"),
            platformProductId = SimpleJson.findString(response, "platform_product_id"),
            amount = SimpleJson.findString(response, "amount")
        )
    }

    private fun post(path: String, body: String): String {
        val url = URL(coreConfig.apiBaseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Product", coreConfig.productName)
            setRequestProperty("Encrypt", if (coreConfig.encrypt) "1" else "0")
            if (!coreConfig.encrypt) {
                setRequestProperty("ProductId", coreConfig.productId)
            }
        }

        val requestBody = ApiRequestEncryption.prepareBody(
            body = body,
            config = coreConfig,
            encrypt = coreConfig.encrypt
        )
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody)
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        val decodedResponse = ApiRequestEncryption.readResponse(
            response = response,
            config = coreConfig,
            encrypt = coreConfig.encrypt
        )
        if (status !in 200..299) {
            throw PaymentException("HTTP $status: $decodedResponse")
        }
        return decodedResponse
    }

    private fun OrderVerificationRequest.toJson(): String {
        return """
            {
              "token": "${token.escapeJson()}",
              "platform_product_id": "${platformProductId.escapeJson()}",
              "uid": "${uid.escapeJson()}",
              "issub": $isSubscription,
              "trade_order_id": "${tradeOrderId.escapeJson()}"
            }
        """.trimIndent()
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
