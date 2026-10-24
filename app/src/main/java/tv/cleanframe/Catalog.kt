package tv.cleanframe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

class AppCatalog(private val context: Context) {
    fun load(): List<TvApp> {
        val manager = context.packageManager
        val seen = linkedMapOf<String, TvApp>()
        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved = runCatching {
                manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())
            for (info in resolved) {
                val packageName = info.activityInfo.packageName
                if (packageName == context.packageName || packageName in Blocked) continue
                if (packageName in seen) continue
                val label = info.loadLabel(manager).toString().trim()
                if (label.isEmpty()) continue
                val icon = runCatching { info.loadIcon(manager).toImageBitmap() }.getOrNull() ?: continue
                seen[packageName] = TvApp(packageName, label, icon)
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private companion object {
        val Blocked = setOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.tv.remote.service",
        )
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmap = when (this) {
        is BitmapDrawable -> bitmap
        else -> {
            val width = intrinsicWidth.coerceAtLeast(1)
            val height = intrinsicHeight.coerceAtLeast(1)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            image
        }
    }
    return bitmap.asImageBitmap()
}

fun launchPackage(context: Context, packageName: String): Boolean {
    val manager = context.packageManager
    val intent = manager.getLeanbackLaunchIntentForPackage(packageName)
        ?: manager.getLaunchIntentForPackage(packageName)
        ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

fun openGoogleTv(context: Context): Boolean {
    val packages = listOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
    )
    return packages.any { launchPackage(context, it) }
}

fun openSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun requestHomeRole(context: Context) {
    val roles = context.getSystemService(android.app.role.RoleManager::class.java) ?: return
    if (!roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return
    if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) return
    val intent = roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
