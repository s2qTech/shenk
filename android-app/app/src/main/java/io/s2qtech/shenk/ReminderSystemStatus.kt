package io.s2qtech.shenk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class ReminderSystemStatus(
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val batteryOptimizationExempt: Boolean,
    val isXiaomiDevice: Boolean,
) {
    val notificationsAllowed: Boolean
        get() = notificationPermissionGranted && notificationsEnabled
}

fun readReminderSystemStatus(context: Context): ReminderSystemStatus {
    val permissionGranted =
        context.packageManager.checkPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            context.packageName,
        ) == PackageManager.PERMISSION_GRANTED
    val powerManager = context.getSystemService(PowerManager::class.java)
    return ReminderSystemStatus(
        notificationPermissionGranted = permissionGranted,
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryOptimizationExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        isXiaomiDevice = isXiaomiDevice(Build.MANUFACTURER, Build.BRAND),
    )
}

internal fun isXiaomiDevice(manufacturer: String?, brand: String?): Boolean =
    manufacturer.equals("xiaomi", ignoreCase = true) ||
        brand.equals("xiaomi", ignoreCase = true) ||
        brand.equals("redmi", ignoreCase = true) ||
        brand.equals("poco", ignoreCase = true)

fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

fun applicationDetailsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
