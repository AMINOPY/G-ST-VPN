package com.aras.client.ui.calculator

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.base.BaseComponentActivity
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.delay

class CalculatorActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        CalculatorScreen(
            onSecretEntered = {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
        )
    }
}

@Composable
private fun CalculatorScreen(onSecretEntered: () -> Unit) {
    val context = LocalContext.current
    var display by remember { mutableStateOf("0") }
    var storedValue by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var startNewNumber by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(false) }
    var welcomeText by remember { mutableStateOf("") }

    LaunchedEffect(showWelcome) {
        if (showWelcome) {
            "WELCOME TO RADAR".forEach { character ->
                welcomeText += character
                delay(48)
            }
            delay(350)
            onSecretEntered()
        }
    }

    val background = if (showWelcome) Color(0xFF020604) else Color(0xFF000000)
    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (showWelcome) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = welcomeText,
                    color = Color(0xFF39FF78),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = display,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                color = Color.White,
                fontSize = 58.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            val rows = listOf(
                listOf("AC", "+/-", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("⌫", "0", ".", "=")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { key ->
                        val isOperator = key in listOf("÷", "×", "−", "+", "=")
                        val isUtility = key in listOf("AC", "+/-", "%", "⌫")
                        val color = when {
                            isOperator -> Color(0xFFFF9F0A)
                            isUtility -> Color(0xFFA5A5A5)
                            else -> Color(0xFF333333)
                        }
                        val textColor = if (isUtility) Color.Black else Color.White
                        var pressed by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) 0.92f else 1f,
                            animationSpec = tween(110, easing = FastOutSlowInEasing),
                            label = "calculator-button-scale"
                        )
                        LaunchedEffect(pressed) {
                            if (pressed) {
                                delay(110)
                                pressed = false
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).padding(vertical = 5.dp).aspectRatio(1f)
                                .scale(scale).background(color, CircleShape)
                                .combinedClickable(onClick = {
                                    pressed = true
                                    when (key) {
                                        "AC" -> { display = "0"; storedValue = null; pendingOperator = null; startNewNumber = false }
                                        "⌫" -> { if (!startNewNumber) display = if (display.length <= 1 || (display.length == 2 && display.startsWith("-"))) "0" else display.dropLast(1) }
                                        "+/-" -> if (display != "0") display = if (display.startsWith("-")) display.drop(1) else "-$display"
                                        "%" -> display = CalculatorMath.format((display.toDoubleOrNull() ?: 0.0) / 100.0)
                                        "." -> if (startNewNumber) { display = "0."; startNewNumber = false } else if (!display.contains(".")) display += "."
                                        "+", "−", "×", "÷" -> {
                                            val current = display.toDoubleOrNull()
                                            val previous = storedValue
                                            val operator = pendingOperator
                                            val shouldChain = previous != null && current != null &&
                                                operator != null && !startNewNumber
                                            val chainedResult = if (shouldChain) {
                                                CalculatorMath.calculate(previous!!, current!!, operator!!)
                                            } else current

                                            if (shouldChain && chainedResult == null) {
                                                display = "Error"
                                                storedValue = null
                                                pendingOperator = null
                                                startNewNumber = true
                                            } else {
                                                if (chainedResult != null) {
                                                    display = CalculatorMath.format(chainedResult)
                                                    storedValue = chainedResult
                                                } else {
                                                    storedValue = current
                                                }
                                                pendingOperator = key
                                                startNewNumber = true
                                            }
                                        }
                                        "=" -> {
                                            val left = storedValue
                                            val right = display.toDoubleOrNull()
                                            val result = if (left != null && right != null && pendingOperator != null) {
                                                CalculatorMath.calculate(left, right, pendingOperator!!)
                                            } else right
                                            display = result?.let { CalculatorMath.format(it) } ?: "Error"
                                            storedValue = null
                                            pendingOperator = null
                                            startNewNumber = true
                                        }
                                        else -> {
                                            val nextDisplay = when {
                                                display == "Error" || startNewNumber || display == "0" -> key
                                                display.length < 16 -> display + key
                                                else -> display
                                            }
                                            display = nextDisplay
                                            startNewNumber = false
                                            if (pendingOperator == null &&
                                                LauncherIconManager.verifyAccessCode(context, nextDisplay)
                                            ) {
                                                showWelcome = true
                                            }
                                        }
                                    }
                                }, onLongClick = {
                                    if (key == "AC") {
                                        LauncherIconManager.apply(context, LauncherIconManager.MODE_DEFAULT)
                                        display = "RESTORED"
                                        storedValue = null
                                        pendingOperator = null
                                        startNewNumber = true
                                    }
                                }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, color = textColor, fontSize = if (key.length > 2) 21.sp else 27.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

