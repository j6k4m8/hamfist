package dev.hamfist.shared

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

data class AppEntry(val packageName: String, val label: String)

object AppCatalog {
    fun list(context: Context, selected: Set<String>): List<AppEntry> {
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        val known = launchable.map { it.packageName }.toSet()
        return (launchable + (selected - known).map { AppEntry(it,it) })
            .filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    fun hasAccess(context: Context): Boolean {
        val expected = ComponentName(context, NotificationRelay::class.java)
        return Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
    }
    fun openAccess(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context,NotificationRelay::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(detail) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
