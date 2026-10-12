package com.artstudio.document.io

/**
 * Self-contained, robust JSON parser and serializer with zero external dependencies.
 * Avoids any Android SDK stubbing or mock issues during unit tests.
 */
object ArtJson {

    /**
     * Serializes any supported Kotlin primitive/collection structure to formatted JSON string.
     */
    fun stringify(value: Any?, indent: Int = 2): String {
        val sb = StringBuilder()
        serializeValue(value, sb, 0, indent)
        return sb.toString()
    }

    private fun serializeValue(value: Any?, sb: StringBuilder, currentIndent: Int, indentStep: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value.toString())
            is Number -> sb.append(value.toString())
            is String -> {
                sb.append('"')
                for (ch in value) {
                    when (ch) {
                        '"' -> sb.append("\\\"")
                        '\\' -> sb.append("\\\\")
                        '\b' -> sb.append("\\b")
                        '\u000C' -> sb.append("\\f")
                        '\n' -> sb.append("\\n")
                        '\r' -> sb.append("\\r")
                        '\t' -> sb.append("\\t")
                        else -> {
                            if (ch.code < 32) {
                                sb.append(String.format("\\u%04x", ch.code))
                            } else {
                                sb.append(ch)
                            }
                        }
                    }
                }
                sb.append('"')
            }
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val nextIndent = currentIndent + indentStep
                val indentStr = " ".repeat(nextIndent)
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append(indentStr)
                    sb.append('"').append(k.toString()).append("\": ")
                    serializeValue(v, sb, nextIndent, indentStep)
                }
                sb.append("\n").append(" ".repeat(currentIndent)).append("}")
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                val nextIndent = currentIndent + indentStep
                val indentStr = " ".repeat(nextIndent)
                var first = true
                for (item in value) {
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append(indentStr)
                    serializeValue(item, sb, nextIndent, indentStep)
                }
                sb.append("\n").append(" ".repeat(currentIndent)).append("]")
            }
            else -> serializeValue(value.toString(), sb, currentIndent, indentStep)
        }
    }

    /**
     * Parses a JSON string into nested Map, List, String, Number, Boolean, or null.
     */
    fun parse(json: String): Any? {
        val parser = JsonParser(json)
        val result = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isEof()) {
            throw IllegalArgumentException("Unexpected trailing tokens after JSON at position ${parser.position}")
        }
        return result
    }

    private class JsonParser(private val src: String) {
        var position = 0

        fun isEof(): Boolean = position >= src.length

        fun skipWhitespace() {
            while (!isEof() && src[position].isWhitespace()) {
                position++
            }
        }

        private fun peek(): Char = if (isEof()) '\u0000' else src[position]

        fun parseValue(): Any? {
            skipWhitespace()
            if (isEof()) throw IllegalArgumentException("Unexpected end of JSON input")
            return when (val ch = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> {
                    if (ch == '-' || ch.isDigit()) {
                        parseNumber()
                    } else {
                        throw IllegalArgumentException("Unexpected character '$ch' at index $position")
                    }
                }
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                val next = peek()
                if (next == ',') {
                    position++
                } else if (next == '}') {
                    position++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' in object at index $position")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return list
            }
            while (true) {
                val value = parseValue()
                list.add(value)
                skipWhitespace()
                val next = peek()
                if (next == ',') {
                    position++
                } else if (next == ']') {
                    position++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' in array at index $position")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (!isEof()) {
                val ch = src[position++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    if (isEof()) throw IllegalArgumentException("Unterminated escape sequence")
                    when (val esc = src[position++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (position + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                            val hex = src.substring(position, position + 4)
                            position += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = position
            if (peek() == '-') position++
            while (!isEof() && src[position].isDigit()) position++
            var isFloating = false
            if (!isEof() && peek() == '.') {
                isFloating = true
                position++
                while (!isEof() && src[position].isDigit()) position++
            }
            if (!isEof() && (peek() == 'e' || peek() == 'E')) {
                isFloating = true
                position++
                if (!isEof() && (peek() == '+' || peek() == '-')) position++
                while (!isEof() && src[position].isDigit()) position++
            }
            val numStr = src.substring(start, position)
            return if (isFloating) {
                numStr.toDouble()
            } else {
                numStr.toLongOrNull() ?: numStr.toDouble()
            }
        }

        private fun parseBoolean(): Boolean {
            if (src.startsWith("true", position)) {
                position += 4
                return true
            }
            if (src.startsWith("false", position)) {
                position += 5
                return false
            }
            throw IllegalArgumentException("Invalid boolean literal at position $position")
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", position)) {
                position += 4
                return null
            }
            throw IllegalArgumentException("Invalid null literal at position $position")
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            if (isEof() || src[position] != expected) {
                throw IllegalArgumentException("Expected '$expected' at index $position, got '${if (isEof()) "EOF" else src[position]}'")
            }
            position++
        }
    }
}
