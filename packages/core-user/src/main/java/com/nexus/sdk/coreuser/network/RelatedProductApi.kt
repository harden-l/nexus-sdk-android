package com.nexus.sdk.coreuser.network

import android.util.Log
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserException
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal class RelatedProductApi(
    private val config: CoreUserConfig
) {
    fun getRelatedProducts(): List<RelatedProduct> {
        if (config.debug) {
            Log.d(TAG, "getRelatedProducts productId=${config.productId} accountName=${config.accountName}")
        }
        val response = post(
            "/related_products",
            mapOf(
                "product_id" to config.productId,
                "account_name" to config.accountName.trim()
            )
        )
        val code = SimpleJson.findRootInt(response, "code") ?: SimpleJson.findInt(response, "code")
        if (code != null && code != 1) {
            val message = SimpleJson.findRootString(response, "message")
                ?: SimpleJson.findString(response, "message")
                ?: "Get related products failed"
            throw CoreUserException(message)
        }
        val products = parseRelatedProducts(response)
        if (config.debug) {
            Log.d(TAG, "getRelatedProducts parsed count=${products.size} ids=${products.joinToString { it.productId }}")
        }
        return products
    }

    private fun post(path: String, values: Map<String, Any?>): String {
        val url = URL(config.apiBaseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Product", config.productName)
            setRequestProperty("Encrypt", if (config.encrypt) "1" else "0")
            if (!config.encrypt) {
                setRequestProperty("ProductId", config.productId)
            }
        }

        val requestBody = ApiRequestEncryption.prepareBody(
            body = SimpleJson.stringify(values),
            config = config,
            encrypt = config.encrypt
        )
        if (config.debug) {
            Log.d(TAG, "POST $path request=$requestBody")
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody)
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response =
            stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        val decodedResponse = ApiRequestEncryption.readResponse(
            response = response,
            config = config,
            encrypt = config.encrypt
        )
        if (config.debug) {
            Log.d(TAG, "POST $path status=$status response=${decodedResponse.preview()}")
        }
        if (status !in 200..299) {
            throw CoreUserException("HTTP $status: $decodedResponse")
        }
        return decodedResponse
    }

    private fun parseRelatedProducts(response: String): List<RelatedProduct> {
        val items = (findObjectsInList(response, "data") + findObjectsInList(response, "list"))
            .distinct()
        return items.mapNotNull { item ->
            val productId = SimpleJson.findString(item, "product_id")
                ?: SimpleJson.findInt(item, "product_id")?.toString()
                ?: return@mapNotNull null
            val productName = SimpleJson.findString(item, "product_name").orEmpty()
            RelatedProduct(
                productId = productId,
                productName = productName.ifBlank { productId },
                description = SimpleJson.findString(item, "description").orEmpty(),
                icon = SimpleJson.findString(item, "icon").orEmpty(),
                downloadUrl = SimpleJson.findString(item, "download_url").orEmpty(),
                packageName = SimpleJson.findString(item, "package_name").orEmpty()
            )
        }
    }

    private fun findObjectsInList(json: String, listKey: String): List<String> {
        val listIndex = json.indexOf("\"$listKey\"")
        if (listIndex < 0) return emptyList()
        val arrayStart = json.indexOf('[', listIndex)
        if (arrayStart < 0) return emptyList()
        val arrayEnd = findMatching(json, arrayStart, '[', ']')
        if (arrayEnd < 0) return emptyList()
        val arrayContent = json.substring(arrayStart + 1, arrayEnd)
        val objects = mutableListOf<String>()
        var index = 0
        while (index < arrayContent.length) {
            val objectStart = arrayContent.indexOf('{', index)
            if (objectStart < 0) break
            val objectEnd = findMatching(arrayContent, objectStart, '{', '}')
            if (objectEnd < 0) break
            objects += arrayContent.substring(objectStart, objectEnd + 1)
            index = objectEnd + 1
        }
        return objects
    }

    private fun findMatching(value: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until value.length) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            if (char == open) depth++
            if (char == close) {
                depth--
                if (depth == 0) return index
            }
        }
        return -1
    }

    private fun String.preview(maxLength: Int = 300): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
    }

    companion object {
        private const val TAG = "RelatedProductApi"
    }
}
