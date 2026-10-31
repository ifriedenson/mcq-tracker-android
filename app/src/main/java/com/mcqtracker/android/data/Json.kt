package com.mcqtracker.android.data

/**
 * Tiny JSON encoder/decoder used by [ProgressStore].
 *
 * The app avoids `org.json` so the store's serialization can be unit tested on the
 * JVM (the Android stub throws), and avoids a JSON dependency to keep the library
 * list to the handoff's minimum.
 */
object Json {

    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long, is Short -> value.toString()
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Float -> encode(value.toDouble())
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            quote(k?.toString() ?: "null") + ":" + encode(v)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> quote(value.toString())
    }

    fun decode(text: String): Any? {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun decodeObject(text: String): Map<String, Any?> =
        decode(text) as? Map<String, Any?> ?: emptyMap()

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (index >= text.length) return null
            return when (text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            index++ // {
            skipWhitespace()
            if (index < text.length && text[index] == '}') {
                index++
                return map
            }
            while (index < text.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (index < text.length && text[index] == ':') index++
                map[key] = parseValue()
                skipWhitespace()
                if (index < text.length && text[index] == ',') {
                    index++
                    continue
                }
                if (index < text.length && text[index] == '}') {
                    index++
                    break
                }
                break
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            index++ // [
            skipWhitespace()
            if (index < text.length && text[index] == ']') {
                index++
                return list
            }
            while (index < text.length) {
                list += parseValue()
                skipWhitespace()
                if (index < text.length && text[index] == ',') {
                    index++
                    continue
                }
                if (index < text.length && text[index] == ']') {
                    index++
                    break
                }
                break
            }
            return list
        }

        private fun parseString(): String {
            if (index < text.length && text[index] == '"') index++
            val sb = StringBuilder()
            while (index < text.length) {
                val c = text[index]
                when {
                    c == '"' -> {
                        index++
                        return sb.toString()
                    }
                    c == '\\' -> {
                        index++
                        if (index >= text.length) return sb.toString()
                        when (val esc = text[index]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(index + 1, minOf(index + 5, text.length))
                                index += 4
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                            else -> sb.append(esc)
                        }
                        index++
                    }
                    else -> {
                        sb.append(c)
                        index++
                    }
                }
            }
            return sb.toString()
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (text.startsWith(literal, index)) index += literal.length
            return value
        }

        private fun parseNumber(): Any? {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] in "-+.eE")) index++
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: 0
        }
    }
}
