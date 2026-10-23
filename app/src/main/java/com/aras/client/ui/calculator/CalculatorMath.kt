package com.aras.client.ui.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/** Expression evaluator used by the calculator. It implements operator precedence and scientific functions. */
internal object CalculatorMath {
    fun calculate(left: Double, right: Double, operator: String): Double? = when (operator) {
        "+" -> left + right
        "−", "-" -> left - right
        "×", "*" -> left * right
        "÷", "/" -> if (right == 0.0) null else left / right
        else -> right
    }

    fun evaluate(source: String, degrees: Boolean = true): Double? = runCatching {
        Parser(source, degrees).parse().also { require(it.isFinite()) }
    }.getOrNull()

    fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        return try {
            val plain = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().ifEmpty { "0" }
            if (plain.length <= 18) plain else String.format(Locale.US, "%.8g", value)
        } catch (_: Exception) {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }

    private class Parser(private val input: String, private val degrees: Boolean) {
        private var pos = 0
        fun parse(): Double {
            val result = expression()
            whitespace()
            require(pos == input.length) { "Unexpected token" }
            return result
        }
        private fun expression(): Double {
            var value = term()
            while (true) {
                whitespace()
                value = when {
                    take('+') -> value + term()
                    take('-') || take('−') -> value - term()
                    else -> return value
                }
            }
        }
        private fun term(): Double {
            var value = power()
            while (true) {
                whitespace()
                value = when {
                    take('*') || take('×') -> value * power()
                    take('/') || take('÷') -> value / power().also { require(it != 0.0) }
                    else -> return value
                }
            }
        }
        private fun power(): Double {
            var value = unary()
            whitespace()
            while (take('%')) value /= 100.0
            whitespace()
            if (take('^')) value = value.pow(power())
            return value
        }
        private fun unary(): Double {
            whitespace()
            if (take('+')) return unary()
            if (take('-') || take('−')) return -unary()
            return atom()
        }
        private fun atom(): Double {
            whitespace()
            if (take('(')) {
                val value = expression()
                whitespace()
                require(take(')')) { "Missing closing parenthesis" }
                return value
            }
            if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) return number()
            val name = identifier()
            return when (name.lowercase(Locale.US)) {
                "pi", "π" -> Math.PI
                "e" -> Math.E
                "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "cbrt", "abs",
                "ln", "log", "log2", "exp", "floor", "ceil", "round" -> {
                    whitespace()
                    require(take('(')) { "Function requires parentheses" }
                    val x = expression()
                    whitespace()
                    require(take(')')) { "Missing function parenthesis" }
                    when (name.lowercase(Locale.US)) {
                        "sin" -> sin(angle(x))
                        "cos" -> cos(angle(x))
                        "tan" -> tan(angle(x))
                        "asin" -> outputAngle(asin(x))
                        "acos" -> outputAngle(acos(x))
                        "atan" -> outputAngle(atan(x))
                        "sqrt" -> sqrt(x)
                        "cbrt" -> cbrt(x)
                        "abs" -> abs(x)
                        "ln" -> ln(x)
                        "log" -> log10(x)
                        "log2" -> log2(x)
                        "exp" -> exp(x)
                        "floor" -> floor(x)
                        "ceil" -> ceil(x)
                        else -> round(x)
                    }
                }
                else -> error("Unknown function")
            }
        }
        private fun number(): Double {
            val start = pos
            var dot = false
            while (pos < input.length) {
                val c = input[pos]
                if (c.isDigit()) pos++
                else if (c == '.' && !dot) { dot = true; pos++ }
                else break
            }
            return input.substring(start, pos).toDouble()
        }
        private fun identifier(): String {
            val start = pos
            while (pos < input.length && (input[pos].isLetter() || input[pos] == 'π')) pos++
            require(pos > start) { "Expected a number" }
            return input.substring(start, pos)
        }
        private fun angle(x: Double) = if (degrees) Math.toRadians(x) else x
        private fun outputAngle(x: Double) = if (degrees) Math.toDegrees(x) else x
        private fun whitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun take(c: Char): Boolean {
            if (pos < input.length && input[pos] == c) { pos++; return true }
            return false
        }
    }
}
