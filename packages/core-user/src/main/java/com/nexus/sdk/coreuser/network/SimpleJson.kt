package com.nexus.sdk.coreuser.network

internal object SimpleJson {
    fun stringify(values: Map<String, Any?>): String {
        return values.entries
            .filter { it.value != null }
            .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "\"${escape(key)}\":${stringifyValue(value)}"
            }
    }

    fun findString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.let(::unescape)
    }

    fun findBoolean(json: String, key: String): Boolean? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    fun findInt(json: String, key: String): Int? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun findRootString(json: String, key: String): String? {
        return findTopLevelValue(json, key)?.takeIf { it.isNotBlank() }
    }

    fun findRootInt(json: String, key: String): Int? {
        return findTopLevelValue(json, key)?.toIntOrNull()
    }

    fun findDataString(json: String, key: String): String? {
        return findDataValue(json, key)?.takeIf { it.isNotBlank() }
    }

    fun findDataBoolean(json: String, key: String): Boolean? {
        return findDataValue(json, key)?.toBooleanStrictOrNull()
    }

    fun findDataObjectMap(json: String): Map<String, Any?> {
        return findTopLevelObject(json, "data")?.let(::objectToMap).orEmpty()
    }

    fun objectToMap(json: String): Map<String, Any?> {
        var cursor = skipWhitespace(json, 0)
        if (cursor >= json.length || json[cursor] != '{') return emptyMap()
        cursor++
        val values = linkedMapOf<String, Any?>()

        while (cursor < json.length) {
            cursor = skipWhitespace(json, cursor)
            if (cursor >= json.length || json[cursor] == '}') break
            val key = readQuoted(json, cursor) ?: break
            cursor = skipWhitespace(json, key.nextIndex)
            if (cursor >= json.length || json[cursor] != ':') break
            cursor = skipWhitespace(json, cursor + 1)
            val value = readAnyValue(json, cursor) ?: break
            values[key.value] = value.value
            cursor = skipWhitespace(json, value.nextIndex)
            if (cursor < json.length && json[cursor] == ',') cursor++
        }
        return values
    }

    private fun findDataValue(json: String, key: String): String? {
        val data = findTopLevelObject(json, "data") ?: return null
        return findTopLevelValue(data, key)
    }

    private fun findTopLevelObject(json: String, key: String): String? {
        return findTopLevelValue(json, key, objectOnly = true)
    }

    private fun findTopLevelValue(json: String, key: String, objectOnly: Boolean = false): String? {
        var index = 0
        var depth = 0
        var inString = false
        var escaped = false

        while (index < json.length) {
            val char = json[index]
            if (inString) {
                escaped = if (escaped) {
                    false
                } else if (char == '\\') {
                    true
                } else {
                    if (char == '"') inString = false
                    false
                }
                index++
                continue
            }

            when (char) {
                '"' -> {
                    if (depth == 1) {
                        val readKey = readQuoted(json, index) ?: return null
                        var cursor = skipWhitespace(json, readKey.nextIndex)
                        if (cursor < json.length && json[cursor] == ':') {
                            cursor = skipWhitespace(json, cursor + 1)
                            if (readKey.value == key) {
                                return readValue(json, cursor, objectOnly)?.value
                            }
                        }
                        index = readKey.nextIndex
                        continue
                    }
                    inString = true
                }
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        return null
    }

    private fun readValue(json: String, startIndex: Int, objectOnly: Boolean): ReadResult? {
        if (startIndex >= json.length) return null
        return when (json[startIndex]) {
            '"' -> {
                if (objectOnly) null else readQuoted(json, startIndex)
            }
            '{' -> readObject(json, startIndex)
            '[' -> readArray(json, startIndex)
            else -> {
                if (objectOnly) return null
                var end = startIndex
                while (end < json.length && json[end] !in setOf(',', '}', ']')) end++
                ReadResult(json.substring(startIndex, end).trim(), end)
            }
        }
    }

    private fun readAnyValue(json: String, startIndex: Int): AnyReadResult? {
        if (startIndex >= json.length) return null
        return when (json[startIndex]) {
            '"' -> readQuoted(json, startIndex)?.let { AnyReadResult(it.value, it.nextIndex) }
            '{' -> readObject(json, startIndex)?.let {
                AnyReadResult(objectToMap(it.value), it.nextIndex)
            }
            '[' -> readArray(json, startIndex)?.let { AnyReadResult(it.value, it.nextIndex) }
            else -> {
                var end = startIndex
                while (end < json.length && json[end] !in setOf(',', '}', ']')) end++
                val rawValue = json.substring(startIndex, end).trim()
                AnyReadResult(parsePrimitive(rawValue), end)
            }
        }
    }

    private fun readObject(json: String, startIndex: Int): ReadResult? {
        var index = startIndex
        var depth = 0
        var inString = false
        var escaped = false
        while (index < json.length) {
            val char = json[index]
            if (inString) {
                escaped = if (escaped) {
                    false
                } else if (char == '\\') {
                    true
                } else {
                    if (char == '"') inString = false
                    false
                }
                index++
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return ReadResult(json.substring(startIndex, index + 1), index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun readArray(json: String, startIndex: Int): ReadResult? {
        var index = startIndex
        var depth = 0
        var inString = false
        var escaped = false
        while (index < json.length) {
            val char = json[index]
            if (inString) {
                escaped = if (escaped) {
                    false
                } else if (char == '\\') {
                    true
                } else {
                    if (char == '"') inString = false
                    false
                }
                index++
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return ReadResult(json.substring(startIndex, index + 1), index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun readQuoted(json: String, startIndex: Int): ReadResult? {
        val builder = StringBuilder()
        var index = startIndex + 1
        var escaped = false
        while (index < json.length) {
            val char = json[index]
            if (escaped) {
                builder.append(
                    when (char) {
                        '"' -> '"'
                        '\\' -> '\\'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> char
                    }
                )
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                return ReadResult(builder.toString(), index + 1)
            } else {
                builder.append(char)
            }
            index++
        }
        return null
    }

    private fun skipWhitespace(json: String, startIndex: Int): Int {
        var index = startIndex
        while (index < json.length && json[index].isWhitespace()) index++
        return index
    }

    private fun stringifyValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Map<*, *> -> stringify(
                value.entries.associate { it.key.toString() to it.value }
            )
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { stringifyValue(it) }
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> "\"${escape(value.toString())}\""
        }
    }

    private fun parsePrimitive(value: String): Any? {
        if (value == "null") return null
        value.toBooleanStrictOrNull()?.let { return it }
        value.toLongOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }
        return value
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private data class ReadResult(
        val value: String,
        val nextIndex: Int
    )

    private data class AnyReadResult(
        val value: Any?,
        val nextIndex: Int
    )
}
