package com.nexus.sdk.payment.products

import android.util.Log
import com.nexus.sdk.coreuser.network.ApiRequestEncryption
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.payment.subscription.PaymentException
import com.nexus.sdk.payment.config.SimpleJson
import com.nexus.sdk.payment.products.Product
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal class ProductApi(
    private val coreConfig: CoreUserConfig
) {
    fun getProducts(): List<Product> {
        val path = "/m/v7/iap/list"
        debug("getProducts start path=$path baseUrl=${coreConfig.apiBaseUrl}")
        return try {
            val response = post(path, "{}")
            val code = SimpleJson.findInt(response, "code")
            val message = SimpleJson.findString(response, "message")
            debug("getProducts business response code=$code message=${message.orEmpty()}")
            if (code != null && code != 1) {
                throw PaymentException(message ?: "Get products failed")
            }
            val products = ProductParser.parseProducts(response)
            debug(
                "getProducts parsed count=${products.size} ids=${
                    products.joinToString(",") { it.marketProductId }
                }"
            )
            products
        } catch (error: Throwable) {
            debug("getProducts failed: ${error.message}", error)
            throw error
        }
    }

    private fun post(path: String, body: String): String {
        val url = URL(coreConfig.apiBaseUrl.trimEnd('/') + path)
        debug(
            "request POST url=$url encrypt=${if (coreConfig.encrypt) "1" else "0"} " +
                "product=${coreConfig.productName} productId=${
                    if (coreConfig.encrypt) "(encrypted)" else coreConfig.productId
                }"
        )
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
        debug("response status=$status length=${decodedResponse.length} preview=${decodedResponse.preview()}")
        if (status !in 200..299) {
            throw PaymentException("HTTP $status: $decodedResponse")
        }
        return decodedResponse
    }

    private fun debug(message: String, error: Throwable? = null) {
        if (!coreConfig.debug) return
        if (error == null) {
            Log.d(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
    }

    private fun String.preview(maxLength: Int = 300): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
    }

    companion object {
        private const val TAG = "PaymentProductApi"
    }
}
