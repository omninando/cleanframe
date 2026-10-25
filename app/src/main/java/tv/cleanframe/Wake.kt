package tv.cleanframe

import android.content.Context
import android.content.Intent
import android.os.SystemClock

object CleanFrameVisible {
    @Volatile
    var showing: Boolean = false
}

private var lastLaunch = 0L

fun launchCleanFrame(context: Context, force: Boolean = false) {
    if (!force && CleanFrameVisible.showing) return
    val now = SystemClock.elapsedRealtime()
    if (!force && now - lastLaunch < 4_000L) return
    lastLaunch = now
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    runCatching { context.startActivity(intent) }
}
