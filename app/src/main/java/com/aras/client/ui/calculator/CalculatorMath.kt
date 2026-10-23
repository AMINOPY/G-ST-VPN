package com.aras.client.ui.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/**
 * Expression evaluator for the hidden all-in-one calculator.
 * It deliberately has no network or Android dependencies, so the math can be unit-tested.
 */
internal object CalculatorMath {
    fun calculate(left: Double, right: Double, operator: String): Double? = when (operator) {
        "+" -> left + right
        "−", "-" -> left - right
        "×", "*" -> left * right
        "÷", "/" -> if (right == 0.0) null else left / right
        else -> right
    }?.takeIf { it.isFinite() }

    fun evaluate(expression: String, degrees: Boolean = true): Double? = try {
        Parser(expression, degrees).parse().takeIf { it.isFinite() }
    } catch (_: ArithmeticException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NumberFormatException) {
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

    private class Parser(private val source: String, private val degrees: Boolean) {
        private var position = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            require(position == source.length) { "Unexpected input" }
            require(result.isFinite()) { "Result is not finite" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    eat('+') -> value + term()
                    eat('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                value = when {
                    eat('*') || eat('×') -> value * power()
                    eat('/') || eat('÷') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Division by zero" }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skipSpaces()
            return if (eat('^')) base.pow(power()) else base
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                eat('+') -> unary()
                eat('-') || eat('−') -> -unary()
                else -> postfix()
            }
        }

        private fun postfix(): Double {
            var value = primary()
            while (true) {
                skipSpaces()
                when {
                    eat('%') -> value /= 100.0
                    eat('!') -> {
                        require(value >= 0.0 && value <= 170.0 && value % 1.0 == 0.0) {
                            "Factorial requires an integer from 0 to 170"
                        }
                        var result = 1.0
                        for (n in 2..value.toInt()) result *= n
                        value = result
                    }
                    else -> return value
                }
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (eat('(')) {
                val value = expression()
                skipSpaces()
                require(eat(')')) { "Missing closing parenthesis" }
                return value
            }
            if (position < source.length && (source[position].isDigit() || source[position] == '.')) {
                return number()
            }
            val name = identifier().lowercase(Locale.US)
            require(name.isNotEmpty()) { "Expected a number" }
            if (name == "pi" || name == "π") return Math.PI
            if (name == "e") return Math.E
            skipSpaces()
            val argument = if (eat('(')) {
                val result = expression()
                skipSpaces()
                require(eat(')')) { "Missing function parenthesis" }
                result
            } else unary()
            val radians = if (degrees) Math.toRadians(argument) else argument
            val result = when (name) {
                "sin" -> sin(radians)
                "cos" -> cos(radians)
                "tan" -> tan(radians)
                "asin", "arcsin" -> inverseAngle(asin(argument))
                "acos", "arccos" -> inverseAngle(acos(argument))
                "atan", "arctan" -> inverseAngle(atan(argument))
                "sinh" -> sinh(argument)
                "cosh" -> cosh(argument)
                "tanh" -> tanh(argument)
                "sqrt" -> sqrt(argument)
                "cbrt" -> cbrt(argument)
                "abs" -> abs(argument)
                "ln" -> ln(argument)
                "log", "log10" -> log10(argument)
                "exp" -> exp(argument)
                "floor" -> floor(argument)
                "ceil" -> ceil(argument)
                "round" -> round(argument)
                "sqr" -> argument * argument
                else -> throw IllegalArgumentException("Unknown function")
            }
            require(result.isFinite()) { "Invalid function domain" }
            return result
        }

        private fun inverseAngle(radians: Double): Double =
            if (degrees) Math.toDegrees(radians) else radians

        private fun number(): Double {
            val start = position
            var dots = 0
            while (position < source.length) {
                val char = source[position]
                if (char == '.') {
                    dots++
                    require(dots <= 1) { "Invalid decimal" }
                    position++
                } else if (char.isDigit()) {
                    position++
                } else {
                    break
                }
            }
            require(position > start && source.substring(start, position) != ".") { "Invalid number" }
            return source.substring(start, position).toDouble()
        }

        private fun identifier(): String {
            skipSpaces()
            val start = position
            while (position < source.length &&
                (source[position].isLetter() || source[position] == 'π')
            ) position++
            return source.substring(start, position)
        }

        private fun eat(char: Char): Boolean {
            if (position < source.length && source[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < source.length && source[position].isWhitespace()) position++
        }
    }
}
