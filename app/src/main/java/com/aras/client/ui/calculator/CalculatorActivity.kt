package com.aras.client.ui.calculator

import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aras.client.ui.base.BaseComponentActivity
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val CalcBlack = Color(0xFF000000)
private val CalcPanel = Color(0xFF1C1C1E)
private val CalcKey = Color(0xFF333336)
private val CalcUtility = Color(0xFFA5A5A7)
private val CalcOrange = Color(0xFFFF9F0A)
private val CalcText = Color(0xFFF5F5F7)
private val CalcMuted = Color(0xFF9A9AA0)

class CalculatorActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        CalculatorScreen(
            onSecretEntered = { openMain() },
            onBiometricUnlock = { requestBiometricUnlock() }
        )
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun requestBiometricUnlock() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            openMain()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    openMain()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock RADAR VPN")
                .setSubtitle("Authenticate to open the app")
                .setNegativeButtonText("Cancel")
                .build()
        )
    }
}

private enum class CalculatorPage { BASIC, SCIENTIFIC, TOOLS, HISTORY, CURRENCY, DATA, SPEED, UNITS, FINANCE, PROGRAMMER, DATES, GRAPH, NOTES }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorScreen(onSecretEntered: () -> Unit, onBiometricUnlock: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var degrees by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(CalculatorPage.BASIC) }
    var showWelcome by remember { mutableStateOf(false) }
    var welcomeText by remember { mutableStateOf("") }
    var gestureArmed by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    val keyBounds = remember { mutableStateMapOf<String, Rect>() }
    var keypadCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val computed = remember(expression, degrees) {
        if (expression.isBlank()) "" else CalculatorMath.evaluate(expression, degrees)?.let(CalculatorMath::format) ?: ""
    }

    LaunchedEffect(showWelcome) {
        if (showWelcome) {
            "WELCOME TO RADAR".forEach { character ->
                welcomeText += character
                kotlinx.coroutines.delay(42)
            }
            kotlinx.coroutines.delay(280)
            onSecretEntered()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CalcBlack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showWelcome) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(welcomeText, color = Color(0xFF39FF78), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Calculator", color = CalcText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { page = CalculatorPage.HISTORY }) {
                        Text("History", color = CalcOrange)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    PageChip("Basic", page == CalculatorPage.BASIC) { page = CalculatorPage.BASIC }
                    PageChip("Scientific", page == CalculatorPage.SCIENTIFIC) { page = CalculatorPage.SCIENTIFIC }
                    PageChip("Tools", page != CalculatorPage.BASIC && page != CalculatorPage.SCIENTIFIC && page != CalculatorPage.HISTORY) { page = CalculatorPage.TOOLS }
                }

                AnimatedContent(targetState = page, label = "calculator-page", modifier = Modifier.weight(1f)) { activePage ->
                    when (activePage) {
                        CalculatorPage.BASIC, CalculatorPage.SCIENTIFIC -> {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = expression.ifBlank { "0" },
                                        color = CalcMuted,
                                        fontSize = 23.sp,
                                        textAlign = TextAlign.End,
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = computed.ifBlank { result.ifBlank { " " } },
                                        color = CalcText,
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().animateContentSize()
                                    )
                                    TextButton(onClick = { degrees = !degrees }) {
                                        Text(if (degrees) "DEG" else "RAD", color = CalcOrange)
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { keypadCoordinates = it }
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                var keepWatching = true
                                                while (keepWatching) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val pressed = event.changes.filter { it.pressed }
                                                    if (pressed.size >= 2 && !gestureArmed) {
                                                        val coords = keypadCoordinates
                                                        if (coords != null) {
                                                            val points = pressed.map { coords.localToRoot(it.position) }
                                                            val zero = keyBounds["0"]
                                                            val one = keyBounds["1"]
                                                            if (zero != null && one != null &&
                                                                points.any { zero.contains(it) } &&
                                                                points.any { one.contains(it) }
                                                            ) {
                                                                gestureArmed = true
                                                                expression = ""
                                                                result = ""
                                                            }
                                                        }
                                                    }
                                                    keepWatching = event.changes.any { it.pressed }
                                                }
                                            }
                                        },
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    if (activePage == CalculatorPage.SCIENTIFIC) {
                                        listOf(
                                            listOf("sin", "cos", "tan", "√"),
                                            listOf("ln", "log", "x²", "xʸ"),
                                            listOf("(", ")", "π", "e"),
                                            listOf("asin", "acos", "atan", "!")
                                        ).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                                row.forEach { key ->
                                                    CalculatorKey(key, CalcPanel, CalcText, Modifier.weight(1f)) {
                                                        expression += when (key) {
                                                            "√" -> "sqrt("
                                                            "x²" -> "^2"
                                                            "xʸ" -> "^"
                                                            "!" -> "!"
                                                            "π" -> "pi"
                                                            "e" -> "e"
                                                            "sin", "cos", "tan", "ln", "log", "asin", "acos", "atan" -> "$key("
                                                            else -> key
                                                        }
                                                        result = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val rows = listOf(
                                        listOf("AC", "+/−", "%", "÷"),
                                        listOf("7", "8", "9", "×"),
                                        listOf("4", "5", "6", "−"),
                                        listOf("1", "2", "3", "+"),
                                        listOf("⌫", "0", ".", "=")
                                    )
                                    rows.forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                            row.forEach { key ->
                                                val keyColor = when {
                                                    key in listOf("÷", "×", "−", "+", "=") -> CalcOrange
                                                    key in listOf("AC", "+/−", "%", "⌫") -> CalcUtility
                                                    else -> CalcKey
                                                }
                                                CalculatorKey(
                                                    key, keyColor, if (keyColor == CalcUtility) Color.Black else CalcText,
                                                    Modifier.weight(1f),
                                                    onLongClick = {
                                                        if (key == "AC") {
                                                            LauncherIconManager.apply(context, LauncherIconManager.MODE_DEFAULT)
                                                            expression = ""
                                                            result = "Icon restored"
                                                        }
                                                    },
                                                    onPosition = { bounds -> keyBounds[key] = bounds }
                                                ) {
                                                    if (gestureArmed && key == "1") {
                                                        gestureArmed = false
                                                        onBiometricUnlock()
                                                        return@CalculatorKey
                                                    }
                                                    when (key) {
                                                        "AC" -> { expression = ""; result = "" }
                                                        "⌫" -> { expression = expression.dropLast(1); result = "" }
                                                        "+/−" -> {
                                                            expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
                                                            result = ""
                                                        }
                                                        "%" -> { expression += "%"; result = "" }
                                                        "." -> {
                                                            val last = expression.takeLastWhile { it.isDigit() || it == '.' }
                                                            if (!last.contains('.')) expression += if (last.isEmpty()) "0." else "."
                                                            result = ""
                                                        }
                                                        "=" -> {
                                                            val answer = CalculatorMath.evaluate(expression, degrees)
                                                            if (answer != null) {
                                                                val formatted = CalculatorMath.format(answer)
                                                                history.add(0, "$expression = $formatted")
                                                                if (history.size > 100) history.removeAt(history.lastIndex)
                                                                expression = formatted
                                                                result = formatted
                                                            } else result = "Error"
                                                        }
                                                        else -> { expression += key; result = "" }
                                                    }
                                                    if (expression.length in 4..12 && expression.all { it.isDigit() } &&
                                                        LauncherIconManager.verifyAccessCode(context, expression)
                                                    ) showWelcome = true
                                                }
                                            }
                                        }
                                    }
                                    if (gestureArmed) Text("Tap 1 to authenticate", color = CalcOrange, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            }
                        }
                        CalculatorPage.TOOLS -> {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("All tools", color = CalcText, fontSize = 26.sp, fontWeight = FontWeight.Light)
                                ToolRow("Currency converter", "Daily reference rates · no API key") { page = CalculatorPage.CURRENCY }
                                ToolRow("Internet & data", "GB, GiB, remaining data and download time") { page = CalculatorPage.DATA }
                                ToolRow("Speed, time & distance", "Travel speed and transfer rates") { page = CalculatorPage.SPEED }
                                ToolRow("Unit converter", "Length, mass, temperature and data") { page = CalculatorPage.UNITS }
                                ToolRow("Finance & percentage", "Discount, tax and interest") { page = CalculatorPage.FINANCE }
                                ToolRow("Programmer", "Decimal, binary and hexadecimal") { page = CalculatorPage.PROGRAMMER }
                                ToolRow("Date calculator", "Difference between two dates") { page = CalculatorPage.DATES }
                                ToolRow("Math notes & graph", "Evaluate formulas and plot y=f(x)") { page = CalculatorPage.GRAPH }
                                ToolRow("Saved notes", "Save an expression for later") { page = CalculatorPage.NOTES }
                            }
                        }
                        CalculatorPage.HISTORY -> {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recent calculations", color = CalcText, fontSize = 24.sp)
                                    TextButton(onClick = { history.clear() }) { Text("Clear", color = CalcOrange) }
                                }
                                if (history.isEmpty()) Text("Your calculations will appear here.", color = CalcMuted)
                                history.forEach { item ->
                                    Text(item, color = CalcText, modifier = Modifier.fillMaxWidth().clickable {
                                        expression = item.substringAfter(" = ", item)
                                        result = ""
                                        page = CalculatorPage.BASIC
                                    }.padding(12.dp))
                                }
                                TextButton(onClick = { page = CalculatorPage.BASIC }) { Text("Back to calculator", color = CalcOrange) }
                            }
                        }
                        CalculatorPage.CURRENCY -> CurrencyTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.DATA -> DataTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.SPEED -> SpeedTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.UNITS -> UnitTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.FINANCE -> FinanceTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.PROGRAMMER -> ProgrammerTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.DATES -> DateTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.GRAPH -> GraphTool { page = CalculatorPage.TOOLS }
                        CalculatorPage.NOTES -> NotesTool { page = CalculatorPage.TOOLS }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) CalcOrange else CalcMuted)) {
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CalculatorKey(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onPosition: ((Rect) -> Unit)? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, animationSpec = tween(100), label = "key-scale")
    Box(
        modifier = modifier.padding(1.dp).aspectRatio(1f).scale(scale)
            .onGloballyPositioned { onPosition?.invoke(it.boundsInRoot()) }
            .background(color, CircleShape)
            .combinedClickable(onClick = { pressed = true; onClick() }, onLongClick = { onLongClick?.invoke() }),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = if (label.length > 2) 14.sp else 25.sp, fontWeight = FontWeight.Medium)
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

@Composable
private fun ToolRow(title: String, summary: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CalcPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = CalcText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = CalcMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Tools", color = CalcOrange) }
            Text(title, color = CalcText, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CalcField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValue, modifier = modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(14.dp))
}

@Composable
private fun ResultCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CalcPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = CalcText, fontSize = 17.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun CurrencyTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf("EUR") }
    var to by remember { mutableStateOf("USD") }
    var message by remember { mutableStateOf("Reference rates, not live trading quotes.") }
    var busy by remember { mutableStateOf(false) }
    ToolScaffold("Currency", onBack) {
        CalcField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcField("From ISO code", from, { from = it.uppercase().take(3) }, Modifier.weight(1f))
            CalcField("To ISO code", to, { to = it.uppercase().take(3) }, Modifier.weight(1f))
        }
        Button(onClick = {
            scope.launch {
                busy = true
                message = try {
                    val value = amount.toDoubleOrNull() ?: error("Enter a valid amount")
                    val rate = CurrencyRatesRepository.rate(context, from, to)
                    val cached = if (rate.cached) " · cached" else ""
                    amount + " " + from + " = " + CalculatorMath.format(value * rate.rate) + " " + to +
                        "\\nRate date: " + rate.date + cached + "\\n1 " + from + " = " + CalculatorMath.format(rate.rate) + " " + to
                } catch (error: Exception) {
                    "Unable to refresh rate: " + (error.message ?: "network unavailable")
                }
                busy = false
            }
        }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = CalcOrange)) {
            Text(if (busy) "Loading…" else "Convert")
        }
        ResultCard(message)
        Text("Source: Frankfurter · open source · no API key. Last successful rates are cached on this device.", color = CalcMuted, fontSize = 12.sp)
    }
}

@Composable
private fun DataTool(onBack: () -> Unit) {
    var total by remember { mutableStateOf("50") }
    var used by remember { mutableStateOf("18") }
    var daily by remember { mutableStateOf("2") }
    var speed by remember { mutableStateOf("25") }
    val t = total.toDoubleOrNull()
    val u = used.toDoubleOrNull()
    val d = daily.toDoubleOrNull()
    val s = speed.toDoubleOrNull()
    val message = if (t != null && u != null && d != null && s != null && t >= 0 && u >= 0 && d > 0 && s > 0) {
        val remaining = (t - u).coerceAtLeast(0.0)
        val hours = t * 8.0 * 1000.0 / s / 3600.0
        "Remaining: " + CalculatorMath.format(remaining) + " GB\\nEstimated at current usage: " +
            CalculatorMath.format(remaining / d) + " days\\nIdeal download time for " + CalculatorMath.format(t) +
            " GB at " + CalculatorMath.format(s) + " Mbps: " + CalculatorMath.format(hours) + " hours"
    } else "Enter valid positive values."
    ToolScaffold("Internet & data", onBack) {
        CalcField("Package size (GB)", total, { total = it })
        CalcField("Used (GB)", used, { used = it })
        CalcField("Average daily use (GB)", daily, { daily = it })
        CalcField("Download speed (Mbps)", speed, { speed = it })
        ResultCard(message)
        Text("GB uses decimal units here. Real-world download times vary with overhead, server and signal.", color = CalcMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SpeedTool(onBack: () -> Unit) {
    var distance by remember { mutableStateOf("120") }
    var hours by remember { mutableStateOf("2") }
    val km = distance.toDoubleOrNull()
    val time = hours.toDoubleOrNull()
    val message = if (km != null && time != null && km >= 0 && time > 0) {
        val speed = km / time
        "Average speed: " + CalculatorMath.format(speed) + " km/h\\n" +
            CalculatorMath.format(speed / 3.6) + " m/s\\nTime for " + km + " km at 100 km/h: " +
            CalculatorMath.format(km / 100) + " hours"
    } else "Enter a distance and a time greater than zero."
    ToolScaffold("Speed, time & distance", onBack) {
        CalcField("Distance (km)", distance, { distance = it })
        CalcField("Time (hours)", hours, { hours = it })
        ResultCard(message)
    }
}

@Composable
private fun UnitTool(onBack: () -> Unit) {
    var value by remember { mutableStateOf("1") }
    var conversion by remember { mutableStateOf("km-mi") }
    val input = value.toDoubleOrNull()
    val options = listOf("km-mi", "kg-lb", "C-F", "GB-GiB", "MB-MiB", "kmh-ms")
    val converted = if (input == null) "Enter a number." else {
        val output = when (conversion) {
            "km-mi" -> input * 0.6213711922
            "kg-lb" -> input * 2.2046226218
            "C-F" -> input * 9 / 5 + 32
            "GB-GiB" -> input * 1_000_000_000.0 / 1_073_741_824.0
            "MB-MiB" -> input * 1_000_000.0 / 1_048_576.0
            else -> input / 3.6
        }
        CalculatorMath.format(input) + " " + conversion.substringBefore('-') + " = " +
            CalculatorMath.format(output) + " " + conversion.substringAfter('-')
    }
    ToolScaffold("Unit converter", onBack) {
        CalcField("Value", value, { value = it })
        options.forEach { option ->
            TextButton(onClick = { conversion = option }) {
                Text((if (conversion == option) "● " else "") + option.replace("-", " → "),
                    color = if (conversion == option) CalcOrange else CalcText)
            }
        }
        ResultCard(converted)
    }
}

@Composable
private fun FinanceTool(onBack: () -> Unit) {
    var amount by remember { mutableStateOf("100") }
    var percent by remember { mutableStateOf("15") }
    val a = amount.toDoubleOrNull()
    val p = percent.toDoubleOrNull()
    val message = if (a != null && p != null && a >= 0) {
        "Percentage amount: " + CalculatorMath.format(a * p / 100) +
            "\\nAfter discount: " + CalculatorMath.format(a * (1 - p / 100)) +
            "\\nAfter increase: " + CalculatorMath.format(a * (1 + p / 100))
    } else "Enter valid values."
    ToolScaffold("Finance & percentage", onBack) {
        CalcField("Amount", amount, { amount = it })
        CalcField("Percent", percent, { percent = it })
        ResultCard(message)
        Text("Loan, compound interest and payment schedules can be added as separate verified finance tools.", color = CalcMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ProgrammerTool(onBack: () -> Unit) {
    var decimal by remember { mutableStateOf("42") }
    val number = decimal.toLongOrNull()
    val message = if (number != null && number >= 0) {
        "Binary: " + number.toString(2) + "\\nOctal: " + number.toString(8) +
            "\\nHexadecimal: " + number.toString(16).uppercase()
    } else "Enter a non-negative 64-bit integer."
    ToolScaffold("Programmer", onBack) {
        CalcField("Decimal integer", decimal, { decimal = it })
        ResultCard(message)
    }
}

@Composable
private fun DateTool(onBack: () -> Unit) {
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var end by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    val message = try {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end))
        "Difference: " + abs(days) + " days" + if (days < 0) "\\nEnd date is before start date." else ""
    } catch (_: Exception) { "Enter dates in YYYY-MM-DD format." }
    ToolScaffold("Date calculator", onBack) {
        CalcField("Start date (YYYY-MM-DD)", start, { start = it })
        CalcField("End date (YYYY-MM-DD)", end, { end = it })
        ResultCard(message)
    }
}

@Composable
private fun GraphTool(onBack: () -> Unit) {
    var formula by remember { mutableStateOf("sin(x)") }
    var degrees by remember { mutableStateOf(true) }
    ToolScaffold("Math notes & graph", onBack) {
        CalcField("Function of x", formula, { formula = it })
        TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "Degrees" else "Radians", color = CalcOrange) }
        Card(colors = CardDefaults.cardColors(containerColor = CalcPanel), shape = RoundedCornerShape(18.dp)) {
            Canvas(Modifier.fillMaxWidth().height(250.dp).padding(8.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                for (i in 1..8) {
                    val x = size.width * i / 9f
                    val y = size.height * i / 9f
                    drawLine(CalcKey, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawLine(CalcKey, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawLine(CalcMuted, Offset(0f, centerY), Offset(size.width, centerY), 1f)
                drawLine(CalcMuted, Offset(centerX, 0f), Offset(centerX, size.height), 1f)
                val path = Path()
                var started = false
                for (i in 0..240) {
                    val xValue = (i - 120) / 12.0
                    val substituted = formula.replace("x", "(" + xValue + ")", ignoreCase = true)
                    val yValue = CalculatorMath.evaluate(substituted, degrees)
                    if (yValue == null || abs(yValue) > 1000) { started = false; continue }
                    val px = size.width * i / 240f
                    val py = centerY - yValue.toFloat() * size.height / 4f
                    if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                }
                drawPath(path, color = CalcOrange, style = Stroke(width = 2.5f))
            }
        }
        Text("Graph preview uses a fixed x range and is intended for exploration, not engineering-grade plotting.", color = CalcMuted, fontSize = 12.sp)
    }
}

@Composable
private fun NotesTool(onBack: () -> Unit) {
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<List<String>>(emptyList()) }
    ToolScaffold("Saved notes", onBack) {
        CalcField("Expression or note", note, { note = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (note.isNotBlank()) saved = listOf(note) + saved },
                colors = ButtonDefaults.buttonColors(containerColor = CalcOrange)) { Text("Save note") }
            Button(onClick = { note = CalculatorMath.evaluate(note)?.let(CalculatorMath::format) ?: "Error" },
                colors = ButtonDefaults.buttonColors(containerColor = CalcPanel)) { Text("Evaluate") }
        }
        if (saved.isEmpty()) Text("Notes are kept for this session in this initial version.", color = CalcMuted)
        saved.forEach { Text(it, color = CalcText, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
    }
}
