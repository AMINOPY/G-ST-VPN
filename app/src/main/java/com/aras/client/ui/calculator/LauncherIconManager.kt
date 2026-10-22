package com.aras.client.ui.calculator

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches only the two declared launcher aliases. It does not touch VPN services,
 * application data, or any other component.
 */
object LauncherIconManager {
    private const val DEFAULT_ALIAS = "com.aras.client.ui.main.DefaultLauncherAlias"
    private const val CALCULATOR_ALIAS = "com.aras.client.ui.calculator.CalculatorLauncherAlias"
    const val MODE_DEFAULT = "default"
    const val MODE_CALCULATOR = "calculator"

    fun apply(context: Context, mode: String) {
        val pm = context.packageManager
        val calculatorSelected = mode == MODE_CALCULATOR
        pm.setComponentEnabledSetting(
            ComponentName(context, DEFAULT_ALIAS),
            if (calculatorSelected) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            ComponentName(context, CALCULATOR_ALIAS),
            if (calculatorSelected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        context.getSharedPreferences("radar_launcher", Context.MODE_PRIVATE)
            .edit().putString("icon_mode", if (calculatorSelected) MODE_CALCULATOR else MODE_DEFAULT).apply()
    }
}
