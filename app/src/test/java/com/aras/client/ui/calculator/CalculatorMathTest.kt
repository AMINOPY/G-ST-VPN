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
    @Test fun evaluatesOrderOfOperations() = assertEquals(14.0, CalculatorMath.evaluate("2+3*4")!!, 0.000001)
    @Test fun evaluatesParentheses() = assertEquals(20.0, CalculatorMath.evaluate("(2+3)*4")!!, 0.000001)
    @Test fun evaluatesSineInDegrees() = assertEquals(0.5, CalculatorMath.evaluate("sin(30)", true)!!, 0.000001)
    @Test fun evaluatesCosineInDegrees() = assertEquals(0.5, CalculatorMath.evaluate("cos(60)", true)!!, 0.000001)
    @Test fun evaluatesSquareRoot() = assertEquals(4.0, CalculatorMath.evaluate("sqrt(16)")!!, 0.000001)
    @Test fun evaluatesPercentAndFactorial() = assertEquals(1.8, CalculatorMath.evaluate("120%+3! * 0.1")!!, 0.000001)
    @Test fun appliesPercentRelativeToLeftOperandForAddition() = assertEquals(220.0, CalculatorMath.evaluate(CalculatorMath.applyPercent("200+10"))!!, 0.000001)
    @Test fun rejectsDivisionByZeroExpression() = assertNull(CalculatorMath.evaluate("4/0"))
}
