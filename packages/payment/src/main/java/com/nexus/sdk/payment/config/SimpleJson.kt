package com.nexus.sdk.payment.config

internal object SimpleJson {
    fun findString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    fun findInt(json: String, key: String): Int? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun findDouble(json: String, key: String): Double? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun findLong(json: String, key: String): Long? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun findBoolean(json: String, key: String): Boolean? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    fun findStringArray(json: String, key: String): List<String> {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return emptyList()
        val arrayStart = json.indexOf('[', keyIndex)
        if (arrayStart < 0) return emptyList()
        val arrayEnd = findMatching(json, arrayStart, '[', ']')
        if (arrayEnd < 0) return emptyList()
        val arrayContent = json.substring(arrayStart + 1, arrayEnd)
        return Regex("\"((?:\\\\.|[^\"])*)\"")
            .findAll(arrayContent)
            .map { it.groupValues[1] }
            .toList()
    }

    fun findObject(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val objectStart = json.indexOf('{', keyIndex)
        if (objectStart < 0) return null
        val objectEnd = findMatching(json, objectStart, '{', '}')
        if (objectEnd < 0) return null
        return json.substring(objectStart, objectEnd + 1)
    }

    fun findObjectsInList(json: String, listKey: String): List<String> {
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
        for (i in start until value.length) {
            val char = value[i]
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
                if (depth == 0) return i
            }
        }
        return -1
    }
}
