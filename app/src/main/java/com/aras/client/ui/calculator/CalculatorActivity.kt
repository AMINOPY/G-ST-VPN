package com.aras.client.ui.calculator

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aras.client.ui.base.BaseComponentActivity
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import kotlin.math.roundToLong

private enum class CalcPage(val label: String) {
    BASIC("Calculator"), SCIENTIFIC("Scientific"), TOOLS("Tools"), CURRENCY("Currency"),
    UNITS("Unit converter"), SPEED("Speed"), DATA("Internet & data"), FINANCE("Finance"), HISTORY("History"), EVERYDAY("Everyday tools"), PROGRAMMER("Programmer"), STATISTICS("Statistics"), DATE("Date calculator")
}

class CalculatorActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        CalculatorScreen(
            onSecretEntered = {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            },
            onBiometricRequested = { onSuccess ->
                requestBiometric(onSuccess)
            }
        )
    }

    private fun requestBiometric(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric unlock is unavailable. Enter your access code instead.", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@CalculatorActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RADAR VPN")
            .setSubtitle("Verify your identity to open the protected app")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorScreen(
    onSecretEntered: () -> Unit,
    onBiometricRequested: (onSuccess: () -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(CalcPage.BASIC) }
    var menuExpanded by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf("") }
    var degrees by remember { mutableStateOf(true) }
    var showWelcome by remember { mutableStateOf(false) }
    var welcomeText by remember { mutableStateOf("") }
    var currencyAmount by remember { mutableStateOf("1") }
    var currencyFrom by remember { mutableStateOf("EUR") }
    var currencyTo by remember { mutableStateOf("USD") }
    var currencyResult by remember { mutableStateOf("") }
    var currencyDate by remember { mutableStateOf("") }
    var currencyError by remember { mutableStateOf("") }
    var currencyLoading by remember { mutableStateOf(false) }
    var unitValue by remember { mutableStateOf("1") }
    var unitFrom by remember { mutableStateOf("km") }
    var unitTo by remember { mutableStateOf("mi") }
    var unitResult by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("100") }
    var travelTime by remember { mutableStateOf("2") }
    var speedResult by remember { mutableStateOf("") }
    var packageGb by remember { mutableStateOf("50") }
    var usedGb by remember { mutableStateOf("18") }
    var dailyGb by remember { mutableStateOf("2") }
    var dataResult by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("10000") }
    var annualRate by remember { mutableStateOf("5") }
    var years by remember { mutableStateOf("5") }
    var financeResult by remember { mutableStateOf("") }
    var percentBase by remember { mutableStateOf("100") }
    var percentValue by remember { mutableStateOf("15") }
    var tipBill by remember { mutableStateOf("50") }
    var tipRate by remember { mutableStateOf("10") }
    var people by remember { mutableStateOf("2") }
    var bmiWeight by remember { mutableStateOf("70") }
    var bmiHeight by remember { mutableStateOf("175") }
    var everydayResult by remember { mutableStateOf("") }
    var programmerValue by remember { mutableStateOf("255") }
    var programmerBase by remember { mutableStateOf("10") }
    var programmerResult by remember { mutableStateOf("") }
    var statisticsValues by remember { mutableStateOf("1, 2, 3, 4, 5") }
    var statisticsResult by remember { mutableStateOf("") }
    var dateStart by remember { mutableStateOf("2026-01-01") }
    var dateEnd by remember { mutableStateOf("2026-12-31") }
    var dateResult by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    val pressedKeys = remember { mutableStateListOf<String>() }
    var simultaneous01Seen by remember { mutableStateOf(false) }
    var biometricArmed by remember { mutableStateOf(false) }

    LaunchedEffect(showWelcome) {
        if (showWelcome) {
            welcomeText = ""
            "WELCOME TO RADAR".forEach { ch ->
                welcomeText += ch
                kotlinx.coroutines.delay(38)
            }
            kotlinx.coroutines.delay(250)
            onSecretEntered()
        }
    }

    val bg = Color(0xFF000000)
    val orange = Color(0xFFFF9F0A)
    val utility = Color(0xFFA5A5A5)
    val digit = Color(0xFF333333)

    Surface(modifier = Modifier.fillMaxSize(), color = bg, contentColor = Color.White) {
        if (showWelcome) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(welcomeText, color = Color(0xFF39FF78), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calculator", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Color.White)
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("Tools  ⌄", color = orange) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            CalcPage.values().filter { it != CalcPage.BASIC }.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { page = option; menuExpanded = false }
                                )
                            }
                        }
                    }
                    if (page != CalcPage.BASIC) {
                        TextButton(onClick = { page = CalcPage.BASIC }) { Text("Done", color = orange) }
                    }
                }

                AnimatedContent(targetState = page, label = "calculator-page") { current ->
                    when (current) {
                        CalcPage.BASIC, CalcPage.SCIENTIFIC -> {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { page = if (page == CalcPage.SCIENTIFIC) CalcPage.BASIC else CalcPage.SCIENTIFIC }) {
                                        Text(if (page == CalcPage.SCIENTIFIC) "Basic" else "Scientific", color = orange)
                                    }
                                    TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "DEG" else "RAD", color = orange) }
                                    TextButton(onClick = { page = CalcPage.HISTORY }) { Text("History", color = orange) }
                                }
                                Text(
                                    text = expression,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                                    color = Color(0xFFBDBDBD), fontSize = 23.sp, textAlign = TextAlign.End, maxLines = 2
                                )
                                Text(
                                    text = result.ifEmpty { expression },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light,
                                    textAlign = TextAlign.End, maxLines = 1
                                )
                                if (current == CalcPage.SCIENTIFIC) {
                                    val sciRows = listOf(
                                        listOf("sin(", "cos(", "tan(", "√("),
                                        listOf("ln(", "log(", "π", "e"),
                                        listOf("x²", "^", "(", ")"),
                                        listOf("asin(", "acos(", "atan(", "abs(")
                                    )
                                    sciRows.forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            row.forEach { key ->
                                                SmallCalcKey(key, Modifier.weight(1f)) {
                                                    when (key) {
                                                        "x²" -> { expression = "($expression)^2"; result = "" }
                                                        else -> { expression = appendToken(expression, key); result = "" }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                val rows = listOf(
                                    listOf("AC", "+/-", "%", "÷"),
                                    listOf("7", "8", "9", "×"),
                                    listOf("4", "5", "6", "−"),
                                    listOf("1", "2", "3", "+"),
                                    listOf("⌫", "0", ".", "=")
                                )
                                rows.forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { key ->
                                            val keyColor = when {
                                                key in listOf("÷", "×", "−", "+", "=") -> orange
                                                key in listOf("AC", "+/-", "%", "⌫") -> utility
                                                else -> digit
                                            }
                                            val keyTextColor = if (key in listOf("AC", "+/-", "%", "⌫")) Color.Black else Color.White
                                            var pressed by remember { mutableStateOf(false) }
                                            val scale by animateFloatAsState(if (pressed) .93f else 1f, tween(100, easing = FastOutSlowInEasing), label = "key-scale")
                                            Box(
                                                Modifier.weight(1f).padding(vertical = 3.dp).aspectRatio(if (page == CalcPage.SCIENTIFIC) 1.3f else 1f).scale(scale)
                                                    .background(keyColor, CircleShape)
                                                    .pointerInput(key) {
                                                        awaitEachGesture {
                                                            awaitFirstDown(requireUnconsumed = false)
                                                            val tracksSecretGesture = key == "0" || key == "1"
                                                            if (tracksSecretGesture && !pressedKeys.contains(key)) pressedKeys.add(key)
                                                            if (pressedKeys.contains("0") && pressedKeys.contains("1")) simultaneous01Seen = true
                                                            try { waitForUpOrCancellation() } finally {
                                                                if (tracksSecretGesture) pressedKeys.remove(key)
                                                                if (pressedKeys.isEmpty() && simultaneous01Seen) {
                                                                    kotlinx.coroutines.delay(180)
                                                                    biometricArmed = true
                                                                    simultaneous01Seen = false
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .combinedClickable(
                                                        onClick = {
                                                            pressed = true
                                                            when (key) {
                                                                "AC" -> { expression = "0"; result = ""; biometricArmed = false }
                                                                "⌫" -> { expression = if (expression.length <= 1) "0" else expression.dropLast(1); result = "" }
                                                                "+/-" -> {
                                                                    expression = if (expression.startsWith("-")) expression.drop(1) else "-($expression)"
                                                                    result = ""
                                                                }
                                                                "%" -> { expression = appendToken(expression, "%"); result = "" }
                                                                "=" -> {
                                                                    val evaluated = CalculatorMath.evaluate(expression, degrees)
                                                                    result = evaluated?.let { CalculatorMath.format(it) } ?: "Error"
                                                                    if (result != "Error") {
                                                                        history.add(0, "$expression = $result")
                                                                        if (history.size > 100) history.removeAt(history.lastIndex)
                                                                    }
                                                                    expression = result
                                                                }
                                                                else -> {
                                                                    if (biometricArmed && key == "1") {
                                                                        biometricArmed = false
                                                                        onBiometricRequested { onSecretEntered() }
                                                                    } else {
                                                                        if (result.isNotEmpty() && (key.firstOrNull()?.isDigit() == true || key == ".")) expression = "0"
                                                                        expression = appendToken(expression, key)
                                                                        result = ""
                                                                        if (LauncherIconManager.verifyAccessCode(context, expression.filter { it.isDigit() }) &&
                                                                            expression.all { it.isDigit() }) {
                                                                            showWelcome = true
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            pressed = false
                                                        },
                                                        onLongClick = {
                                                            if (key == "AC") {
                                                                LauncherIconManager.apply(context, LauncherIconManager.MODE_DEFAULT)
                                                                expression = "0"
                                                                result = "Default icon restored"
                                                            }
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(key, color = keyTextColor, fontSize = if (key.length > 2) 16.sp else 25.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                                Text("Hold AC to restore the default app icon", color = Color(0xFF777777), fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                        CalcPage.TOOLS -> ToolsMenu(onSelect = { page = it })
                        CalcPage.CURRENCY -> {
                            ToolPage("Currency converter", onBack = { page = CalcPage.TOOLS }) {
                                Field("Amount", currencyAmount, { currencyAmount = it }, true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("From (ISO)", currencyFrom, { currencyFrom = it.uppercase(Locale.US).take(3) }, modifier = Modifier.weight(1f))
                                    Field("To (ISO)", currencyTo, { currencyTo = it.uppercase(Locale.US).take(3) }, modifier = Modifier.weight(1f))
                                }
                                Button(onClick = {
                                    currencyLoading = true; currencyError = ""; currencyResult = ""
                                    scope.launch {
                                        val response = withContext(Dispatchers.IO) { fetchCurrency(currencyFrom, currencyTo) }
                                        currencyLoading = false
                                        response.fold(
                                            onSuccess = { (rate, date) ->
                                                val amount = currencyAmount.toBigDecimalOrNull()
                                                currencyResult = amount?.multiply(rate.toBigDecimal()).stripTrailingZeros().toPlainString()
                                                    ?: "Invalid amount"
                                                currencyDate = date
                                                context.getSharedPreferences("calculator_currency_cache", 0).edit()
                                                    .putString("${currencyFrom}_${currencyTo}_rate", rate.toString())
                                                    .putString("${currencyFrom}_${currencyTo}_date", date)
                                                    .apply()
                                            },
                                            onFailure = {
                                                val prefs = context.getSharedPreferences("calculator_currency_cache", 0)
                                                val cachedRate = prefs.getString("${currencyFrom}_${currencyTo}_rate", null)?.toBigDecimalOrNull()
                                                val cachedDate = prefs.getString("${currencyFrom}_${currencyTo}_date", null)
                                                val amount = currencyAmount.toBigDecimalOrNull()
                                                if (cachedRate != null && amount != null) {
                                                    currencyResult = amount.multiply(cachedRate).stripTrailingZeros().toPlainString()
                                                    currencyDate = cachedDate ?: "unknown"
                                                    currencyError = "Network unavailable; showing cached reference rate."
                                                } else {
                                                    currencyError = it.message ?: "Rates unavailable"
                                                }
                                            }
                                        )
                                    }
                                }, enabled = !currencyLoading && currencyFrom.length == 3 && currencyTo.length == 3) {
                                    Text(if (currencyLoading) "Loading…" else "Convert")
                                }
                                if (currencyResult.isNotEmpty()) Text("$currencyResult $currencyTo", color = orange, fontSize = 28.sp, fontWeight = FontWeight.Light)
                                if (currencyDate.isNotEmpty()) Text("Reference rate date: $currencyDate", color = Color.LightGray, fontSize = 12.sp)
                                if (currencyError.isNotEmpty()) Text(currencyError, color = Color(0xFFFF7777))
                                Text("Free daily reference rates, no API key. Rates are indicative, not live trading quotes.", color = Color.LightGray, fontSize = 12.sp)
                                Text("Source: Frankfurter · frankfurter.dev", color = Color(0xFF999999), fontSize = 11.sp)
                            }
                        }
                        CalcPage.UNITS -> {
                            ToolPage("Unit converter", onBack = { page = CalcPage.TOOLS }) {
                                Field("Value", unitValue, { unitValue = it }, true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("From (m, km, mi, kg, lb, °C)", unitFrom, { unitFrom = it.trim() }, modifier = Modifier.weight(1f))
                                    Field("To", unitTo, { unitTo = it.trim() }, modifier = Modifier.weight(1f))
                                }
                                Button(onClick = {
                                    unitResult = convertUnit(unitValue.toDoubleOrNull(), unitFrom, unitTo)?.let { CalculatorMath.format(it) }
                                        ?: "Unsupported units"
                                }) { Text("Convert") }
                                Text(unitResult, color = orange, fontSize = 26.sp)
                                Text("Supports length, mass, time, and Celsius/Fahrenheit/Kelvin conversions.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        CalcPage.SPEED -> {
                            ToolPage("Speed · time · distance", onBack = { page = CalcPage.TOOLS }) {
                                Field("Distance (km)", distance, { distance = it }, true)
                                Field("Time (hours)", travelTime, { travelTime = it }, true)
                                Button(onClick = {
                                    val d = distance.toDoubleOrNull()
                                    val t = travelTime.toDoubleOrNull()
                                    speedResult = if (d != null && t != null && t > 0) "${CalculatorMath.format(d / t)} km/h · ${CalculatorMath.format(d * 1000 / (t * 3600))} m/s" else "Enter a valid distance and positive time"
                                }) { Text("Calculate speed") }
                                Text(speedResult, color = orange, fontSize = 24.sp)
                            }
                        }
                        CalcPage.DATA -> {
                            ToolPage("Internet & data calculator", onBack = { page = CalcPage.TOOLS }) {
                                Field("Package size (GB)", packageGb, { packageGb = it }, true)
                                Field("Used (GB)", usedGb, { usedGb = it }, true)
                                Field("Average daily usage (GB)", dailyGb, { dailyGb = it }, true)
                                Button(onClick = {
                                    val total = packageGb.toDoubleOrNull()
                                    val used = usedGb.toDoubleOrNull()
                                    val daily = dailyGb.toDoubleOrNull()
                                    dataResult = if (total != null && used != null && daily != null && total >= 0 && used >= 0 && daily > 0 && used <= total) {
                                        val left = total - used
                                        "$left GB remaining · about ${CalculatorMath.format(left / daily)} days at this usage"
                                    } else "Check values: usage must not exceed package size and daily usage must be positive"
                                }) { Text("Calculate remaining data") }
                                Text(dataResult, color = orange, fontSize = 20.sp)
                                Text("GB uses decimal units here (1 GB = 1,000 MB). Estimates do not read device traffic automatically.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        CalcPage.FINANCE -> {
                            ToolPage("Finance calculator", onBack = { page = CalcPage.TOOLS }) {
                                Field("Principal / loan amount", principal, { principal = it }, true)
                                Field("Annual interest rate (%)", annualRate, { annualRate = it }, true)
                                Field("Years", years, { years = it }, true)
                                Button(onClick = {
                                    val p = principal.toDoubleOrNull()
                                    val annual = annualRate.toDoubleOrNull()
                                    val duration = years.toDoubleOrNull()
                                    financeResult = if (p != null && annual != null && duration != null && p >= 0 && annual >= 0 && duration > 0) {
                                        val months = duration * 12
                                        val r = annual / 1200
                                        val payment = if (r == 0.0) p / months else p * r / (1 - (1 + r).pow(-months))
                                        "Estimated monthly payment: ${CalculatorMath.format(payment)}\nTotal paid: ${CalculatorMath.format(payment * months)}"
                                    } else "Enter valid values; years must be positive"
                                }) { Text("Calculate loan") }
                                Text(financeResult, color = orange, fontSize = 18.sp)
                                Text("Estimate excludes fees, insurance, and lender-specific rounding.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        CalcPage.HISTORY -> {
                            ToolPage("History", onBack = { page = CalcPage.BASIC }) {
                                if (history.isEmpty()) Text("Your calculations will appear here.", color = Color.LightGray)
                                history.forEach { item ->
                                    TextButton(onClick = { expression = item.substringBefore(" = "); result = item.substringAfter(" = "); page = CalcPage.BASIC }) {
                                        Text(item, color = Color.White)
                                    }
                                }
                                TextButton(onClick = { history.clear() }) { Text("Clear history", color = orange) }
                            }
                        }
                        CalcPage.EVERYDAY -> {
                            ToolPage("Everyday tools", onBack = { page = CalcPage.TOOLS }) {
                                Text("Percentage · discount · tax", fontSize = 18.sp)
                                Field("Base amount", percentBase, { percentBase = it }, true)
                                Field("Percentage (%)", percentValue, { percentValue = it }, true)
                                Button(onClick = {
                                    val base = percentBase.toDoubleOrNull()
                                    val rate = percentValue.toDoubleOrNull()
                                    everydayResult = if (base != null && rate != null) {
                                        val part = base * rate / 100.0
                                        "Percentage: ${CalculatorMath.format(part)}\nAfter discount: ${CalculatorMath.format(base - part)}\nAfter addition/tax: ${CalculatorMath.format(base + part)}"
                                    } else "Enter valid numbers"
                                }) { Text("Calculate percentage") }
                                Text(everydayResult, color = orange, fontSize = 17.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Tip and split bill", fontSize = 18.sp)
                                Field("Bill amount", tipBill, { tipBill = it }, true)
                                Field("Tip (%)", tipRate, { tipRate = it }, true)
                                Field("People", people, { people = it }, true)
                                Button(onClick = {
                                    val bill = tipBill.toDoubleOrNull()
                                    val rate = tipRate.toDoubleOrNull()
                                    val count = people.toIntOrNull()
                                    everydayResult = if (bill != null && rate != null && count != null && bill >= 0 && rate >= 0 && count > 0) {
                                        val total = bill * (1 + rate / 100)
                                        "Tip: ${CalculatorMath.format(bill * rate / 100)}\nTotal: ${CalculatorMath.format(total)}\nPer person: ${CalculatorMath.format(total / count)}"
                                    } else "Enter a valid bill, tip and people count"
                                }) { Text("Calculate tip and split") }
                                Spacer(Modifier.height(8.dp))
                                Text("BMI estimate", fontSize = 18.sp)
                                Field("Weight (kg)", bmiWeight, { bmiWeight = it }, true)
                                Field("Height (cm)", bmiHeight, { bmiHeight = it }, true)
                                Button(onClick = {
                                    val weight = bmiWeight.toDoubleOrNull()
                                    val height = bmiHeight.toDoubleOrNull()?.div(100)
                                    everydayResult = if (weight != null && height != null && weight > 0 && height > 0) {
                                        "BMI: ${CalculatorMath.format(weight / (height * height))} · screening estimate only"
                                    } else "Enter valid weight and height"
                                }) { Text("Calculate BMI") }
                            }
                        }
                        CalcPage.PROGRAMMER -> {
                            ToolPage("Programmer calculator", onBack = { page = CalcPage.TOOLS }) {
                                Field("Integer value", programmerValue, { programmerValue = it.trim() })
                                SettingsBaseSelector(programmerBase, { programmerBase = it })
                                Button(onClick = {
                                    val base = programmerBase.toIntOrNull() ?: 10
                                    val number = programmerValue.toLongOrNull(base)
                                    programmerResult = if (number != null) {
                                        "Decimal: $number\nBinary: ${number.toString(2)}\nOctal: ${number.toString(8)}\nHex: ${number.toString(16).uppercase(Locale.US)}"
                                    } else "Invalid integer for base $base"
                                }) { Text("Convert number bases") }
                                Text(programmerResult, color = orange, fontSize = 18.sp)
                            }
                        }
                        CalcPage.STATISTICS -> {
                            ToolPage("Statistics", onBack = { page = CalcPage.TOOLS }) {
                                Field("Numbers separated by commas", statisticsValues, { statisticsValues = it })
                                Button(onClick = {
                                    val values = statisticsValues.split(",", ";", " ").mapNotNull { it.trim().toDoubleOrNull() }
                                    statisticsResult = if (values.isNotEmpty()) {
                                        val sorted = values.sorted()
                                        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
                                        "Count: ${values.size}\nMean: ${CalculatorMath.format(values.average())}\nMedian: ${CalculatorMath.format(median)}\nMin: ${CalculatorMath.format(sorted.first())}\nMax: ${CalculatorMath.format(sorted.last())}"
                                    } else "Enter at least one valid number"
                                }) { Text("Calculate statistics") }
                                Text(statisticsResult, color = orange, fontSize = 18.sp)
                            }
                        }
                        CalcPage.DATE -> {
                            ToolPage("Date calculator", onBack = { page = CalcPage.TOOLS }) {
                                Field("Start date (YYYY-MM-DD)", dateStart, { dateStart = it })
                                Field("End date (YYYY-MM-DD)", dateEnd, { dateEnd = it })
                                Button(onClick = {
                                    dateResult = runCatching {
                                        val start = java.time.LocalDate.parse(dateStart)
                                        val end = java.time.LocalDate.parse(dateEnd)
                                        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end)
                                        "$days days between dates\nInclusive count: ${kotlin.math.abs(days) + 1} days"
                                    }.getOrElse { "Use valid dates in YYYY-MM-DD format" }
                                }) { Text("Calculate date difference") }
                                Text(dateResult, color = orange, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsBaseSelector(value: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Input base:", color = Color.LightGray)
        listOf("2", "8", "10", "16").forEach { base ->
            TextButton(onClick = { onChange(base) }) {
                Text(base, color = if (base == value) Color(0xFFFF9F0A) else Color.White)
            }
        }
    }
}

private fun appendToken(current: String, token: String): String {
    if (current == "0" && token.firstOrNull()?.isDigit() == true) return token
    if (current == "Error" || current == "Default icon restored") return if (token.length == 1 && token[0].isDigit()) token else "0$token"
    return when (token) {
        "sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "sqrt(", "ln(", "log(", "abs(" ->
            if (current == "0") token else "$current$token"
        "π", "e" -> if (current == "0") token else "$current$token"
        "." -> if (current.split(Regex("[+−×÷()^]")).lastOrNull()?.contains(".") != true) current + "." else current
        else -> current + token
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallCalcKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .94f else 1f, tween(100), label = "scientific-key")
    Box(modifier.padding(vertical = 2.dp).aspectRatio(2f).scale(scale).background(Color(0xFF242424), RoundedCornerShape(14.dp))
        .combinedClickable(onClick = { pressed = true; onClick(); pressed = false }), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ToolsMenu(onSelect: (CalcPage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Advanced tools", fontSize = 24.sp, fontWeight = FontWeight.Light)
        CalcPage.values().filter { it !in listOf(CalcPage.BASIC, CalcPage.SCIENTIFIC, CalcPage.TOOLS) }.forEach { item ->
            Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, modifier = Modifier.weight(1f), fontSize = 17.sp)
                    Text("›", color = Color(0xFFFF9F0A), fontSize = 24.sp)
                }
            }
        }
        Text("Scientific functions, conversion, data estimates and finance tools are available offline except currency rates.", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun ToolPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Tools", color = Color(0xFFFF9F0A)) }
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Light)
        content()
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Ascii)
    )
}

private suspend fun fetchCurrency(from: String, to: String): Result<Pair<Double, String>> = runCatching {
    require(from.matches(Regex("[A-Z]{3}")) && to.matches(Regex("[A-Z]{3}"))) { "Use 3-letter ISO currency codes." }
    if (from == to) return@runCatching Pair(1.0, java.time.LocalDate.now().toString())
    val connection = URL("https://api.frankfurter.dev/v2/rates?base=$from&quotes=$to").openConnection()
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.getInputStream().bufferedReader().use { reader ->
        val json = JSONArray(reader.readText())
        require(json.length() > 0) { "No rate returned for $from/$to." }
        val row = json.getJSONObject(0)
        Pair(row.getDouble("rate"), row.getString("date"))
    }
}

private fun convertUnit(value: Double?, from: String, to: String): Double? {
    if (value == null || !value.isFinite()) return null
    val f = from.lowercase(Locale.US).replace(" ", "")
    val t = to.lowercase(Locale.US).replace(" ", "")
    if (f == t) return value
    val length = mapOf("m" to 1.0, "km" to 1000.0, "cm" to .01, "mm" to .001, "mi" to 1609.344, "ft" to .3048, "in" to .0254)
    val mass = mapOf("kg" to 1.0, "g" to .001, "mg" to .000001, "lb" to .45359237, "oz" to .028349523125)
    val time = mapOf("s" to 1.0, "sec" to 1.0, "min" to 60.0, "h" to 3600.0, "hr" to 3600.0, "day" to 86400.0)
    if (f in length && t in length) return value * length.getValue(f) / length.getValue(t)
    if (f in mass && t in mass) return value * mass.getValue(f) / mass.getValue(t)
    if (f in time && t in time) return value * time.getValue(f) / time.getValue(t)
    val celsius = when (f) { "c", "°c" -> value; "f", "°f" -> (value - 32) * 5 / 9; "k" -> value - 273.15; else -> return null }
    return when (t) { "c", "°c" -> celsius; "f", "°f" -> celsius * 9 / 5 + 32; "k" -> celsius + 273.15; else -> null }
}
