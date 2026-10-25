package live.hisui.calorietracker.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isRestricted(context: Context): Boolean =
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun settingsIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appInfoIntent(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun tipsUrl(): String = "https://dontkillmyapp.com/${Build.MANUFACTURER.lowercase().replace(' ', '_')}"
}
