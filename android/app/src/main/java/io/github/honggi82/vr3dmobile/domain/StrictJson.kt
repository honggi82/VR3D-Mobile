package io.github.honggi82.vr3dmobile.domain

import java.math.BigDecimal

sealed interface JsonValue
data class JsonObject(val values: Map<String, JsonValue>) : JsonValue
data class JsonArray(val values: List<JsonValue>) : JsonValue
data class JsonString(val value: String) : JsonValue
data class JsonNumber(val value: BigDecimal) : JsonValue
data class JsonBoolean(val value: Boolean) : JsonValue
data object JsonNull : JsonValue

class JsonFormatException(message: String) : IllegalArgumentException(message)

object StrictJson {
    fun parse(text: String): JsonValue = Parser(text).parse()

    private class Parser(private val text: String) {
        private var position = 0

        fun parse(): JsonValue {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            if (position != text.length) fail("Unexpected trailing data")
            return value
        }

        private fun parseValue(depth: Int): JsonValue {
            if (depth > 32) fail("JSON nesting is too deep")
            if (position >= text.length) fail("Unexpected end of JSON")
            return when (text[position]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> JsonString(parseString())
                't' -> parseLiteral("true", JsonBoolean(true))
                'f' -> parseLiteral("false", JsonBoolean(false))
                'n' -> parseLiteral("null", JsonNull)
                '-', in '0'..'9' -> parseNumber()
                else -> fail("Unexpected character")
            }
        }

        private fun parseObject(depth: Int): JsonObject {
            position++
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (consume('}')) return JsonObject(values)
            while (true) {
                if (position >= text.length || text[position] != '"') fail("Object key must be a string")
                val key = parseString()
                if (values.containsKey(key)) fail("Duplicate object key: $key")
                skipWhitespace()
                requireChar(':')
                skipWhitespace()
                values[key] = parseValue(depth)
                skipWhitespace()
                if (consume('}')) break
                requireChar(',')
                skipWhitespace()
            }
            return JsonObject(values)
        }

        private fun parseArray(depth: Int): JsonArray {
            position++
            skipWhitespace()
            val values = mutableListOf<JsonValue>()
            if (consume(']')) return JsonArray(values)
            while (true) {
                values += parseValue(depth)
                skipWhitespace()
                if (consume(']')) break
                requireChar(',')
                skipWhitespace()
            }
            return JsonArray(values)
        }

        private fun parseString(): String {
            requireChar('"')
            val result = StringBuilder()
            while (position < text.length) {
                val char = text[position++]
                when {
                    char == '"' -> return result.toString()
                    char == '\\' -> result.append(parseEscape())
                    char.code < 0x20 -> fail("Control character in string")
                    else -> result.append(char)
                }
                if (result.length > 524_288) fail("JSON string is too long")
            }
            fail("Unterminated string")
        }

        private fun parseEscape(): Char {
            if (position >= text.length) fail("Unterminated escape")
            return when (val escaped = text[position++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (position + 4 > text.length) fail("Incomplete unicode escape")
                    val value = text.substring(position, position + 4).toIntOrNull(16)
                        ?: fail("Invalid unicode escape")
                    position += 4
                    value.toChar()
                }
                else -> fail("Invalid escape: $escaped")
            }
        }

        private fun parseNumber(): JsonNumber {
            val start = position
            if (consume('-') && position >= text.length) fail("Incomplete number")
            if (consume('0')) {
                if (position < text.length && text[position].isDigit()) fail("Leading zero in number")
            } else {
                if (position >= text.length || text[position] !in '1'..'9') fail("Invalid number")
                while (position < text.length && text[position].isDigit()) position++
            }
            if (consume('.')) {
                if (position >= text.length || !text[position].isDigit()) fail("Invalid fraction")
                while (position < text.length && text[position].isDigit()) position++
            }
            if (position < text.length && (text[position] == 'e' || text[position] == 'E')) {
                position++
                if (position < text.length && (text[position] == '+' || text[position] == '-')) position++
                if (position >= text.length || !text[position].isDigit()) fail("Invalid exponent")
                while (position < text.length && text[position].isDigit()) position++
            }
            return try {
                JsonNumber(BigDecimal(text.substring(start, position)))
            } catch (_: NumberFormatException) {
                fail("Invalid number")
            }
        }

        private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, position)) fail("Invalid literal")
            position += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (position < text.length && text[position] in " \t\r\n") position++
        }

        private fun consume(char: Char): Boolean {
            if (position < text.length && text[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun requireChar(char: Char) {
            if (!consume(char)) fail("Expected '$char'")
        }

        private fun fail(message: String): Nothing = throw JsonFormatException("$message at offset $position")
    }
}

fun JsonValue.objectValue(name: String): JsonObject = this as? JsonObject
    ?: throw JsonFormatException("$name must be an object")

fun JsonValue.arrayValue(name: String): List<JsonValue> = (this as? JsonArray)?.values
    ?: throw JsonFormatException("$name must be an array")

fun JsonValue.stringValue(name: String): String = (this as? JsonString)?.value
    ?: throw JsonFormatException("$name must be a string")

fun JsonValue.longValue(name: String): Long = try {
    (this as? JsonNumber)?.value?.longValueExact()
        ?: throw JsonFormatException("$name must be an integer")
} catch (_: ArithmeticException) {
    throw JsonFormatException("$name must be an integer")
}

fun JsonObject.exactKeys(name: String, expected: Set<String>) {
    val missing = expected - values.keys
    val extra = values.keys - expected
    if (missing.isNotEmpty() || extra.isNotEmpty()) {
        throw JsonFormatException("$name keys are invalid; missing=$missing extra=$extra")
    }
}

fun JsonObject.required(name: String): JsonValue = values[name]
    ?: throw JsonFormatException("Missing key: $name")
