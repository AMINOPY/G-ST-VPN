package com.aras.client.ui.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal object CalculatorMath {
    fun calculate(left: Double, right: Double, operator: String): Double? = when (operator) {
        "+" -> left + right
        "−" -> left - right
        "×" -> left * right
        "÷" -> if (right == 0.0) null else left / right
        else -> right
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        return try {
            BigDecimal.valueOf(value)
                .setScale(8, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
                .take(18)
                .ifEmpty { "0" }
        } catch (_: Exception) {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }
}
