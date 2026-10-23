package com.aras.client.ui.calculator

import android.content.Intent
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aras.client.ui.base.BaseComponentActivity
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlin.math.max

private val CalcBlack = Color(0xFF000000)
private val CalcPanel = Color(0xFF191919)
private val CalcDigit = Color(0xFF333333)
private val CalcUtility = Color(0xFFA5A5A5)
private val CalcOrange = Color(0xFFFF9F0A)
private val CalcWhite = Color(0xFFF8F8F8)
private val CalcGreen = Color(0xFF39D98A)

class CalculatorActivity : BaseComponentActivity() {
    private fun openRadar() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun requestBiometricEntry() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric unlock is unavailable. Use your access code.", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    openRadar()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@CalculatorActivity, errString, Toast.LENGTH_SHORT).show()
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RADAR VPN")
            .setSubtitle("Confirm your identity to continue")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Cancel")
            .build())
    }

    @Composable
    override fun ScreenContent() {
        CalculatorScreen({ openRadar() }, { requestBiometricEntry() })
    }
}

@Composable
private fun CalculatorScreen(onSecretEntered: () -> Unit, onBiometricRequested: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf("Basic") }
    var expression by remember { mutableStateOf("0") }
    var answer by remember { mutableStateOf("") }
    var degreeMode by remember { mutableStateOf(true) }
    var showWelcome by remember { mutableStateOf(false) }
    var welcomeText by remember { mutableStateOf("") }
    var codeCandidate by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    val heldKeys = remember { mutableStateListOf<String>() }
    val chordKeys = remember { mutableStateListOf<String>() }
    var chordDetected by remember { mutableStateOf(false) }
    var gestureArmed by remember { mutableStateOf(false) }

    LaunchedEffect(showWelcome) {
        if (showWelcome) {
            welcomeText = ""
            "WELCOME TO RADAR".forEach { character -> welcomeText += character; delay(42) }
            delay(220)
            onSecretEntered()
        }
    }
    val computed = remember(expression, degreeMode) {
        if (expression.isBlank() || expression == "Error") null else CalculatorMath.evaluate(expression, degreeMode)
    }
    fun resetCodeCandidate() { codeCandidate = "" }
    fun pressKey(key: String) {
        when (key) {
            "AC" -> { expression = "0"; answer = ""; resetCodeCandidate() }
            "⌫" -> { expression = if (expression.length <= 1) "0" else expression.dropLast(1); answer = ""; resetCodeCandidate() }
            "+/−" -> { expression = if (expression.startsWith("-")) expression.drop(1) else if (expression == "0") "-0" else "-(" + expression + ")"; answer = ""; resetCodeCandidate() }
            "=" -> {
                val value = CalculatorMath.evaluate(expression, degreeMode)
                if (value == null) { expression = "Error"; answer = "" }
                else {
                    val formatted = CalculatorMath.format(value)
                    history.add(0, expression to formatted)
                    while (history.size > 100) history.removeAt(history.lastIndex)
                    expression = formatted
                    answer = ""
                }
                resetCodeCandidate()
            }
            "sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "sqrt", "abs", "exp" -> {
                expression = if (expression == "0" || expression == "Error") key + "(" else expression + key + "("
                answer = ""; resetCodeCandidate()
            }
            "π" -> { expression = if (expression == "0" || expression == "Error") "π" else expression + "π"; answer = ""; resetCodeCandidate() }
            "e" -> { expression = if (expression == "0" || expression == "Error") "e" else expression + "e"; answer = ""; resetCodeCandidate() }
            "x²" -> { expression = "(" + expression + ")^2"; answer = ""; resetCodeCandidate() }
            "√" -> { expression = if (expression == "0" || expression == "Error") "sqrt(" else expression + "sqrt("; answer = ""; resetCodeCandidate() }
            else -> {
                if (key.length == 1 && key[0].isDigit()) {
                    expression = if (expression == "0" || expression == "Error") key else expression + key
                    if (expression.all { it.isDigit() } && codeCandidate.length < 12) {
                        codeCandidate += key
                        if (LauncherIconManager.verifyAccessCode(context, codeCandidate)) showWelcome = true
                    } else resetCodeCandidate()
                } else {
                    expression = if (expression == "0" || expression == "Error") { if (key == ".") "0." else key } else expression + key
                    resetCodeCandidate()
                }
                answer = ""
            }
        }
    }

    if (showWelcome) {
        Box(Modifier.fillMaxSize().background(CalcBlack), contentAlignment = Alignment.Center) {
            Text(welcomeText, color = CalcGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(CalcBlack).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Calculator", color = CalcWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("RADAR • PRIVATE TOOL", color = Color(0xFF888888), fontSize = 10.sp, letterSpacing = 1.2.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (degreeMode) "DEG" else "RAD", color = CalcOrange, fontSize = 12.sp,
                    modifier = Modifier.background(CalcPanel, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
                        .pointerInput(degreeMode) { detectTapGestures(onTap = { degreeMode = !degreeMode }) })
                Text("⋯", color = CalcWhite, fontSize = 25.sp,
                    modifier = Modifier.background(CalcPanel, CircleShape).padding(horizontal = 12.dp, vertical = 3.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { page = if (page == "Tools") "Basic" else "Tools" }) })
            }
        }
        Row(Modifier.fillMaxWidth().background(CalcPanel, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("Basic", "Scientific", "Tools", "Notes", "History").forEach { tab ->
                val selected = page == tab
                Text(tab, color = if (selected) CalcBlack else Color(0xFFBBBBBB), fontSize = 10.sp, maxLines = 1,
                    modifier = Modifier.weight(1f).background(if (selected) CalcWhite else Color.Transparent, RoundedCornerShape(20.dp))
                        .padding(vertical = 9.dp).pointerInput(tab) { detectTapGestures(onTap = { page = tab }) }, textAlign = TextAlign.Center)
            }
        }
        when (page) {
            "Tools" -> ToolsPanel(modifier = Modifier.weight(1f))
            "Notes" -> NotesPanel(degreeMode, modifier = Modifier.weight(1f))
            "History" -> HistoryPanel(history, onSelect = { expression = it; page = "Basic" }, onClear = { history.clear() }, modifier = Modifier.weight(1f))
            else -> {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                    Text(expression, color = CalcWhite, fontSize = if (expression.length > 18) 27.sp else if (expression.length > 12) 36.sp else 52.sp,
                        fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Text(computed?.let { "= " + CalculatorMath.format(it) } ?: answer, color = Color(0xFF8D8D8D),
                        fontSize = 23.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp))
                }
                if (page == "Scientific") {
                    val scientificRows = listOf(
                        listOf("sin", "cos", "tan", "ln", "log"),
                        listOf("asin", "acos", "atan", "sqrt", "abs"),
                        listOf("π", "e", "^", "(", ")"),
                        listOf("x²", "exp", "!", "RAD/DEG", "⌫")
                    )
                    scientificRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { key ->
                                CalculatorKey(key, Modifier.weight(1f), if (key == "RAD/DEG") CalcOrange else CalcDigit, compact = true, onClick = {
                                    if (key == "RAD/DEG") degreeMode = !degreeMode
                                    else if (key == "!") { expression += "!"; answer = ""; resetCodeCandidate() }
                                    else pressKey(key)
                                })
                            }
                        }
                    }
                }
                val rows = listOf(listOf("AC", "+/−", "%", "÷"), listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("⌫", "0", ".", "="))
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { key ->
                            val color = when {
                                key in listOf("÷", "×", "−", "+", "=") -> CalcOrange
                                key in listOf("AC", "+/−", "%", "⌫") -> CalcUtility
                                else -> CalcDigit
                            }
                            CalculatorKey(key, Modifier.weight(1f), color, compact = page == "Scientific", onClick = { pressKey(key) },
                                onPressStart = {
                                    if (key == "0" || key == "1") {
                                        if (!heldKeys.contains(key)) heldKeys.add(key)
                                        if (heldKeys.contains("0") && heldKeys.contains("1") && !chordDetected) {
                                            chordDetected = true
                                            chordKeys.clear()
                                            chordKeys.addAll(listOf("0", "1"))
                                            gestureArmed = false
                                        }
                                    }
                                },
                                onPressEnd = {
                                    heldKeys.remove(key)
                                    if (chordDetected && heldKeys.isEmpty()) { chordDetected = false; gestureArmed = true }
                                },
                                onTapOverride = {
                                    if (chordKeys.contains(key)) chordKeys.remove(key)
                                    else if (key == "1" && gestureArmed) { gestureArmed = false; onBiometricRequested() }
                                    else pressKey(key)
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    label: String, modifier: Modifier = Modifier, color: Color = CalcDigit, compact: Boolean = false,
    onClick: () -> Unit, onPressStart: () -> Unit = {}, onPressEnd: () -> Unit = {}, onTapOverride: (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(100, easing = FastOutSlowInEasing), label = "keyScale")
    val tap by rememberUpdatedState(onTapOverride ?: onClick)
    Box(modifier.padding(vertical = if (compact) 2.dp else 3.dp).aspectRatio(if (compact) 1.25f else 1f).scale(scale)
        .background(color, CircleShape).pointerInput(label) {
            detectTapGestures(onPress = {
                pressed = true
                onPressStart()
                try { tryAwaitRelease() } finally { pressed = false; onPressEnd() }
            }, onTap = { tap() })
        }, contentAlignment = Alignment.Center) {
        Text(label, color = if (color == CalcUtility) Color.Black else Color.White,
            fontSize = if (compact) 12.sp else if (label.length > 3) 15.sp else 25.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ToolsPanel(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf("Data") }
    var distance by rememberSaveable { mutableStateOf("120") }
    var hours by rememberSaveable { mutableStateOf("2") }
    var totalGb by rememberSaveable { mutableStateOf("50") }
    var usedGb by rememberSaveable { mutableStateOf("18") }
    var dailyGb by rememberSaveable { mutableStateOf("2") }
    var speedMbps by rememberSaveable { mutableStateOf("25") }
    var amount by rememberSaveable { mutableStateOf("100") }
    var discount by rememberSaveable { mutableStateOf("15") }
    var unitAmount by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("km → mi") }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Data", "Speed", "Units", "Finance").forEach {
                Text(it, color = if (selected == it) CalcBlack else CalcWhite, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).background(if (selected == it) CalcOrange else CalcPanel, RoundedCornerShape(18.dp))
                        .padding(vertical = 10.dp).pointerInput(it) { detectTapGestures(onTap = { selected = it }) }, textAlign = TextAlign.Center)
            }
        }
        when (selected) {
            "Speed" -> {
                Text("Speed · Distance ÷ Time", color = CalcWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                NumberField("Distance (km)", distance, onValue = { distance = it })
                NumberField("Time (hours)", hours, onValue = { hours = it })
                ResultCard("Average speed", if ((hours.toDoubleOrNull() ?: 0.0) > 0) CalculatorMath.format((distance.toDoubleOrNull() ?: 0.0) / (hours.toDoubleOrNull() ?: 1.0)) + " km/h" else "Enter a time greater than zero")
                ResultCard("Meters per second", if ((hours.toDoubleOrNull() ?: 0.0) > 0) CalculatorMath.format((distance.toDoubleOrNull() ?: 0.0) / (hours.toDoubleOrNull() ?: 1.0) / 3.6) + " m/s" else "—")
            }
            "Data" -> {
                Text("Internet & Data", color = CalcWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                NumberField("Package size (GB)", totalGb, onValue = { totalGb = it })
                NumberField("Used (GB)", usedGb, onValue = { usedGb = it })
                NumberField("Average daily use (GB)", dailyGb, onValue = { dailyGb = it })
                NumberField("Download speed (Mbps)", speedMbps, onValue = { speedMbps = it })
                val total = max(0.0, totalGb.toDoubleOrNull() ?: 0.0)
                val used = max(0.0, usedGb.toDoubleOrNull() ?: 0.0)
                val remaining = max(0.0, total - used)
                val daily = dailyGb.toDoubleOrNull() ?: 0.0
                ResultCard("Remaining data", CalculatorMath.format(remaining) + " GB")
                ResultCard("Estimated days remaining", if (daily > 0) CalculatorMath.format(remaining / daily) + " days" else "Enter daily use")
                val mbps = speedMbps.toDoubleOrNull() ?: 0.0
                ResultCard("Ideal download time for package", if (mbps > 0) {
                    val seconds = total * 8000.0 / mbps
                    if (seconds < 60) seconds.toInt().toString() + " sec" else if (seconds < 3600) CalculatorMath.format(seconds / 60) + " min" else CalculatorMath.format(seconds / 3600) + " hr"
                } else "Enter speed")
                Text("Estimates use decimal GB and ideal throughput; network overhead and speed variation are not included.", color = Color.Gray, fontSize = 11.sp)
            }
            "Units" -> {
                Text("Unit Converter", color = CalcWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                NumberField("Value", unitAmount, onValue = { unitAmount = it })
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("km → mi", "mi → km", "kg → lb", "lb → kg", "°C → °F", "°F → °C").chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { option ->
                                Text(option, color = if (unit == option) CalcBlack else CalcWhite, fontSize = 11.sp,
                                    modifier = Modifier.weight(1f).background(if (unit == option) CalcOrange else CalcPanel, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 7.dp, vertical = 9.dp).pointerInput(option) { detectTapGestures(onTap = { unit = option }) }, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                val v = unitAmount.toDoubleOrNull() ?: 0.0
                val converted = when (unit) {
                    "km → mi" -> v * 0.621371
                    "mi → km" -> v / 0.621371
                    "kg → lb" -> v * 2.2046226218
                    "lb → kg" -> v / 2.2046226218
                    "°C → °F" -> v * 9 / 5 + 32
                    else -> (v - 32) * 5 / 9
                }
                ResultCard("Converted value", CalculatorMath.format(converted))
                Text("More categories can include length, area, volume, time, pressure, energy, power and data units.", color = Color.Gray, fontSize = 11.sp)
            }
            else -> {
                Text("Discount & Finance", color = CalcWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                NumberField("Original price", amount, onValue = { amount = it })
                NumberField("Discount (%)", discount, onValue = { discount = it })
                val original = amount.toDoubleOrNull() ?: 0.0
                val pct = discount.toDoubleOrNull() ?: 0.0
                ResultCard("Discount amount", CalculatorMath.format(original * pct / 100.0))
                ResultCard("Price after discount", CalculatorMath.format(original * (1.0 - pct / 100.0)))
                Text("More tools can include compound interest, loan payments, tax, tips and currency conversion with a timestamped rate source.", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NotesPanel(degrees: Boolean, modifier: Modifier = Modifier) {
    var formula by rememberSaveable { mutableStateOf("sin(30)+sqrt(16)") }
    var note by rememberSaveable { mutableStateOf("") }
    var slope by rememberSaveable { mutableStateOf("1") }
    var intercept by rememberSaveable { mutableStateOf("0") }
    val evaluated = CalculatorMath.evaluate(formula, degrees)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Math Notes", color = CalcWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Evaluate a formula and keep a local note.", color = Color.Gray, fontSize = 12.sp)
        NumberField("Formula", formula, onValue = { formula = it })
        ResultCard("Result", evaluated?.let { CalculatorMath.format(it) } ?: "Check the formula")
        NumberField("Note", note, onValue = { note = it })
        Text("Linear graph · y = mx + b", color = CalcWhite, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Slope (m)", slope, Modifier.weight(1f), onValue = { slope = it })
            NumberField("Intercept (b)", intercept, Modifier.weight(1f), onValue = { intercept = it })
        }
        LinearGraph(slope.toDoubleOrNull() ?: 1.0, intercept.toDoubleOrNull() ?: 0.0)
        Text("Notes stay in this screen session in this initial version.", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun LinearGraph(slope: Double, intercept: Double) {
    Canvas(Modifier.fillMaxWidth().height(180.dp).background(CalcPanel, RoundedCornerShape(16.dp)).padding(8.dp)) {
        val midX = size.width / 2
        val midY = size.height / 2
        drawLine(Color.DarkGray, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2f)
        drawLine(Color.DarkGray, Offset(midX, 0f), Offset(midX, size.height), strokeWidth = 2f)
        val points = (0..100).map { index ->
            val x = -5.0 + index * 0.1
            val y = slope * x + intercept
            Offset(((x + 5.0) / 10.0 * size.width).toFloat(), midY - y.toFloat() * (size.height / 12f))
        }
        points.zipWithNext().forEach { (a, b) ->
            if (a.y in 0f..size.height && b.y in 0f..size.height) drawLine(CalcOrange, a, b, strokeWidth = 3f)
        }
    }
}

@Composable
private fun HistoryPanel(history: List<Pair<String, String>>, onSelect: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", color = CalcWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Clear", color = CalcOrange, modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClear() }) })
        }
        if (history.isEmpty()) Text("Your calculations will appear here.", color = Color.Gray, fontSize = 13.sp)
        history.forEach { (formula, value) ->
            Column(Modifier.fillMaxWidth().background(CalcPanel, RoundedCornerShape(14.dp)).padding(14.dp)
                .pointerInput(formula) { detectTapGestures(onTap = { onSelect(formula) }) }) {
                Text(formula, color = Color.Gray, fontSize = 13.sp, maxLines = 2)
                Text(value, color = CalcWhite, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Column(modifier.fillMaxWidth().background(CalcPanel, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        BasicTextField(value, onValueChange = { onValue(it.filter { c -> c.isLetterOrDigit() || c in ".-+*/()π% !→°" }.take(100)) },
            textStyle = TextStyle(color = CalcWhite, fontSize = 17.sp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultCard(label: String, value: String) {
    Column(Modifier.fillMaxWidth().background(CalcPanel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = CalcWhite, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    }
}
