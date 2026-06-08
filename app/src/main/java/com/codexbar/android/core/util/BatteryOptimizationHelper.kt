package com.codexbar.android.core.util

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.codexbar.android.core.oem.OemCompatibility

object BatteryOptimizationHelper {

    /**
     * Returns true if the app is already exempt from battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an intent to request battery optimization exemption directly.
     * Uses ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for a one-tap dialog.
     */
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return OemCompatibility.batteryOptimizationIntent(context)
    }

    /**
     * Fallback: opens the system battery optimization settings page
     * where the user can manually exempt the app.
     */
    fun openBatteryOptimizationSettingsIntent(context: Context): Intent {
        return OemCompatibility.batteryOptimizationIntent(context)
    }

    fun openAutostartSettingsIntent(context: Context): Intent {
        return OemCompatibility.autostartIntent(context)
    }

    fun openNotificationSettingsIntent(context: Context): Intent {
        return OemCompatibility.notificationSettingsIntent(context)
    }
}
