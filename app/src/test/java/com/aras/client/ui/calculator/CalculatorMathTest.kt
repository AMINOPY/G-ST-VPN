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
}
