package com.aras.client.ui.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/**
 * Local expression engine used by the hidden calculator. Supports arithmetic,
 * parentheses, powers, postfix percent, constants and scientific functions.
 */
internal object CalculatorMath {
    fun calculate(left: Double, right: Double, operator: String): Double? = when (operator) {
        "+" -> left + right
        "−", "-" -> left - right
        "×", "*" -> left * right
        "÷", "/" -> if (right == 0.0) null else left / right
        "^" -> left.pow(right)
        else -> right
    }

    fun evaluate(expression: String, degrees: Boolean = true): Double? = try {
        ExpressionParser(expression, degrees).parse().takeIf { it.isFinite() }
    } catch (_: Exception) {
        null
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        if (value == 0.0) return "0"
        return try {
            val plain = BigDecimal.valueOf(value)
                .setScale(10, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            if (plain.length <= 18) plain else String.format(Locale.US, "%.8g", value)
        } catch (_: Exception) {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }

    private class ExpressionParser(private val input: String, private val degrees: Boolean) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            require(position == input.length) { "Unexpected input" }
            require(value.isFinite()) { "Non-finite result" }
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') || consume('×') -> value * power()
                    consume('/') || consume('÷') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Division by zero" }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            var value = unary()
            skipSpaces()
            if (consume('^')) value = value.pow(power())
            return value
        }

        private fun unary(): Double {
            skipSpaces()
            if (consume('+')) return unary()
            if (consume('-')) return -unary()
            var value = primary()
            while (true) {
                skipSpaces()
                when {
                    consume('%') -> value /= 100.0
                    consume('!') -> {
                        require(value >= 0.0 && value <= 170.0 && floor(value) == value) { "Invalid factorial" }
                        var product = 1.0
                        for (factor in 2..value.toInt()) product *= factor
                        value = product
                    }
                    else -> return value
                }
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')')) { "Missing closing parenthesis" }
                return value
            }
            if (position < input.length && (input[position].isLetter())) {
                val name = identifier().lowercase(Locale.US)
                skipSpaces()
                if (name == "pi" || name == "π") return Math.PI
                if (name == "e") return Math.E
                require(consume('(')) { "Function requires parentheses" }
                val argument = expression()
                skipSpaces()
                require(consume(')')) { "Missing closing parenthesis" }
                val angle = if (degrees) Math.toRadians(argument) else argument
                return when (name) {
                    "sin" -> sin(angle)
                    "cos" -> cos(angle)
                    "tan" -> tan(angle)
                    "asin", "arcsin" -> angleOutput(asin(argument))
                    "acos", "arccos" -> angleOutput(acos(argument))
                    "atan", "arctan" -> angleOutput(atan(argument))
                    "sqrt" -> sqrt(argument)
                    "ln" -> ln(argument)
                    "log", "log10" -> log10(argument)
                    "abs" -> abs(argument)
                    "exp" -> exp(argument)
                    "floor" -> floor(argument)
                    "ceil" -> ceil(argument)
                    "round" -> round(argument)
                    else -> error("Unknown function")
                }
            }
            return number()
        }

        private fun angleOutput(value: Double): Double =
            if (degrees) Math.toDegrees(value) else value

        private fun identifier(): String {
            val start = position
            while (position < input.length && (input[position].isLetter() || input[position] == 'π')) position++
            return input.substring(start, position)
        }

        private fun number(): Double {
            skipSpaces()
            val start = position
            var dotSeen = false
            while (position < input.length) {
                val c = input[position]
                if (c.isDigit()) position++
                else if ((c == '.' || c == ',') && !dotSeen) {
                    dotSeen = true
                    position++
                } else break
            }
            require(position > start) { "Expected a number" }
            return input.substring(start, position).replace(',', '.').toDouble()
        }

        private fun consume(char: Char): Boolean {
            if (position < input.length && input[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) position++
        }
    }
}
