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

    @Test fun respectsOperatorPrecedence() = assertEquals(14.0, CalculatorMath.evaluate("2+3*4")!!, 1e-9)
    @Test fun supportsParentheses() = assertEquals(20.0, CalculatorMath.evaluate("(2+3)*4")!!, 1e-9)
    @Test fun supportsPowersAndSquareRoot() = assertEquals(16.0, CalculatorMath.evaluate("sqrt(64)+2^2")!!, 1e-9)
    @Test fun supportsSineInDegrees() = assertEquals(0.5, CalculatorMath.evaluate("sin(30)", true)!!, 1e-9)
    @Test fun supportsCosineInRadians() = assertEquals(1.0, CalculatorMath.evaluate("cos(0)", false)!!, 1e-9)
    @Test fun supportsConstants() = assertEquals(Math.PI, CalculatorMath.evaluate("pi")!!, 1e-9)
    @Test fun supportsPercentAndFactorial() = assertEquals(1.2, CalculatorMath.evaluate("5!%")!!, 1e-9)
    @Test fun invalidExpressionsReturnNull() = assertNull(CalculatorMath.evaluate("5/0"))
}
