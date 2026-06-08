package com.codexbar.android.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

enum class OemFamily {
    OPPO,
    ONEPLUS,
    REALME,
    XIAOMI,
    VIVO,
    HUAWEI,
    HONOR,
    SAMSUNG,
    GOOGLE,
    GENERIC
}

enum class OemSettingKind {
    NOTIFICATIONS,
    BATTERY,
    AUTOSTART,
    APP_DETAILS
}

data class OemSetupStep(
    val kind: OemSettingKind,
    val title: String,
    val description: String,
    val intent: Intent
)

data class OemProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val family: OemFamily,
    val summary: String,
    val strictBackgroundPolicy: Boolean,
    val setupSteps: List<OemSetupStep>
) {
    val displayName: String
        get() = when (family) {
            OemFamily.OPPO -> "OPPO / ColorOS"
            OemFamily.ONEPLUS -> "OnePlus / OxygenOS"
            OemFamily.REALME -> "realme UI"
            OemFamily.XIAOMI -> "Xiaomi / HyperOS"
            OemFamily.VIVO -> "vivo / OriginOS"
            OemFamily.HUAWEI -> "Huawei / HarmonyOS"
            OemFamily.HONOR -> "Honor / MagicOS"
            OemFamily.SAMSUNG -> "Samsung / One UI"
            OemFamily.GOOGLE -> "Google / Pixel"
            OemFamily.GENERIC -> "Android"
        }
}

object OemCompatibility {

    fun profile(context: Context): OemProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val family = detectFamily(manufacturer, brand)

        val strict = family in setOf(
            OemFamily.OPPO,
            OemFamily.ONEPLUS,
            OemFamily.REALME,
            OemFamily.XIAOMI,
            OemFamily.VIVO,
            OemFamily.HUAWEI,
            OemFamily.HONOR
        )

        return OemProfile(
            manufacturer = manufacturer.ifBlank { "Unknown" },
            brand = brand.ifBlank { "Unknown" },
            model = Build.MODEL.orEmpty().ifBlank { "Unknown model" },
            family = family,
            summary = summaryFor(family, strict),
            strictBackgroundPolicy = strict,
            setupSteps = setupSteps(context, family)
        )
    }

    fun notificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.takeIf { it.canResolve(context) } ?: appDetailsIntent(context)
    }

    fun batteryOptimizationIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = packageUri },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetailsIntent(context)
        )
        return firstResolvable(context, candidates)
    }

    fun autostartIntent(context: Context): Intent {
        val packageName = context.packageName
        val family = detectFamily(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
        val candidates = when (family) {
            OemFamily.OPPO, OemFamily.ONEPLUS, OemFamily.REALME -> listOf(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerControlActivity"),
                componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.BatteryUsageActivity"),
                appDetailsIntent(context)
            )
            OemFamily.XIAOMI -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                componentIntent("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                appDetailsIntent(context)
            )
            OemFamily.VIVO -> listOf(
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                appDetailsIntent(context)
            )
            OemFamily.HUAWEI, OemFamily.HONOR -> listOf(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                appDetailsIntent(context)
            )
            OemFamily.SAMSUNG -> listOf(
                componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                appDetailsIntent(context)
            )
            OemFamily.GOOGLE, OemFamily.GENERIC -> listOf(appDetailsIntent(context))
        }

        return firstResolvable(context, candidates).apply {
            putExtra("package_name", packageName)
            putExtra("packageName", packageName)
            putExtra("pkg_name", packageName)
        }
    }

    fun appDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun setupSteps(context: Context, family: OemFamily): List<OemSetupStep> {
        val autostartTitle = when (family) {
            OemFamily.OPPO -> "Allow ColorOS background launch"
            OemFamily.ONEPLUS -> "Allow OxygenOS background launch"
            OemFamily.REALME -> "Allow realme UI background launch"
            OemFamily.XIAOMI -> "Allow autostart"
            OemFamily.VIVO -> "Allow background startup"
            OemFamily.HUAWEI, OemFamily.HONOR -> "Allow app launch"
            else -> "Open app background settings"
        }

        return buildList {
            add(
                OemSetupStep(
                    kind = OemSettingKind.NOTIFICATIONS,
                    title = "Enable notifications",
                    description = "Required for persistent quota status and reset alerts.",
                    intent = notificationSettingsIntent(context)
                )
            )
            add(
                OemSetupStep(
                    kind = OemSettingKind.BATTERY,
                    title = "Use unrestricted battery mode",
                    description = "Keeps quota and token refresh jobs from being delayed aggressively.",
                    intent = batteryOptimizationIntent(context)
                )
            )
            add(
                OemSetupStep(
                    kind = OemSettingKind.AUTOSTART,
                    title = autostartTitle,
                    description = "Needed on strict OEM builds such as ColorOS 16 for boot reschedule and background sync.",
                    intent = autostartIntent(context)
                )
            )
            add(
                OemSetupStep(
                    kind = OemSettingKind.APP_DETAILS,
                    title = "Open app details",
                    description = "Fallback page for permissions, battery, data usage, and notification controls.",
                    intent = appDetailsIntent(context)
                )
            )
        }
    }

    private fun summaryFor(family: OemFamily, strict: Boolean): String {
        return when {
            family == OemFamily.OPPO -> "ColorOS can stop periodic jobs unless notifications, battery mode, and auto-launch are explicitly allowed."
            strict -> "This Android build often applies extra background limits. Enable the reliability steps below for stable quota refresh."
            else -> "Standard Android background scheduling is expected to work. Notifications and battery settings are still available if refreshes become stale."
        }
    }

    private fun detectFamily(manufacturer: String, brand: String): OemFamily {
        val normalized = "${manufacturer.lowercase(Locale.US)} ${brand.lowercase(Locale.US)}"
        return when {
            normalized.contains("oppo") || normalized.contains("coloros") || normalized.contains("oplus") -> OemFamily.OPPO
            normalized.contains("oneplus") -> OemFamily.ONEPLUS
            normalized.contains("realme") -> OemFamily.REALME
            normalized.contains("xiaomi") || normalized.contains("redmi") || normalized.contains("poco") -> OemFamily.XIAOMI
            normalized.contains("vivo") || normalized.contains("iqoo") -> OemFamily.VIVO
            normalized.contains("huawei") -> OemFamily.HUAWEI
            normalized.contains("honor") -> OemFamily.HONOR
            normalized.contains("samsung") -> OemFamily.SAMSUNG
            normalized.contains("google") -> OemFamily.GOOGLE
            else -> OemFamily.GENERIC
        }
    }

    private fun componentIntent(packageName: String, className: String): Intent {
        return Intent().apply {
            component = ComponentName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun firstResolvable(context: Context, intents: List<Intent>): Intent {
        return intents.firstOrNull { it.canResolve(context) }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: appDetailsIntent(context)
    }

    private fun Intent.canResolve(context: Context): Boolean {
        return resolveActivity(context.packageManager) != null
    }
}
