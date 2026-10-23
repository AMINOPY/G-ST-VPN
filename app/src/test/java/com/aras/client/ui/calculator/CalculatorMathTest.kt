package com.aras.client.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorMathTest {
    @Test fun adds() = assertEquals(10.0, CalculatorMath.calculate(7.0, 3.0, "+")!!, 0.0)
    @Test fun subtracts() = assertEquals(4.0, CalculatorMath.calculate(7.0, 3.0, "−")!!, 0.0)
    @Test fun multiplies() = assertEquals(21.0, CalculatorMath.calculate(7.0, 3.0, "×")!!, 0.0)
    @Test fun divides() = assertEquals(2.5, CalculatorMath.calculate(5.0, 2.0, "÷")!!, 0.0)
    @Test fun divisionByZeroIsErrorResult() = assertNull(CalculatorMath.calculate(5.0, 0.0, "÷"))
    @Test fun formatsWholeNumbersWithoutDecimalTail() = assertEquals("42", CalculatorMath.format(42.0))
    @Test fun formatsDecimalResults() = assertEquals("2.5", CalculatorMath.format(2.5))
    @Test fun rejectsNonFiniteResults() = assertEquals("Error", CalculatorMath.format(Double.NaN))
    @Test fun honorsOperatorPrecedence() = assertEquals(14.0, CalculatorMath.evaluate("2+3*4")!!, 0.000001)
    @Test fun evaluatesParentheses() = assertEquals(20.0, CalculatorMath.evaluate("(2+3)*4")!!, 0.000001)
    @Test fun evaluatesSineInDegrees() = assertEquals(1.0, CalculatorMath.evaluate("sin(90)", true)!!, 0.000001)
    @Test fun evaluatesSineInRadians() = assertEquals(1.0, CalculatorMath.evaluate("sin(1.5707963267948966)", false)!!, 0.000001)
    @Test fun evaluatesPostfixPercent() = assertEquals(50.0, CalculatorMath.evaluate("100*50%")!!, 0.000001)
    @Test fun rejectsDivisionByZero() = assertNull(CalculatorMath.evaluate("12/0"))
    @Test fun evaluatesExpressionsWithVariables() = assertEquals(10.0, CalculatorMath.evaluate("x^2+1", true, mapOf("x" to 3.0))!!, 0.000001)
}
