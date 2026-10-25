package tv.cleanframe

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo

class WakeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val launch = Runnable { launchCleanFrame(this) }
    private val reclaim = Runnable { launchCleanFrame(this, force = true) }
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_DREAMING_STOPPED) {
                schedule()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_DREAMING_STOPPED)
            }
            registerReceiver(receiver, filter)
            registered = true
        }
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive != false) schedule()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            launchCleanFrame(this, force = true)
            handler.removeCallbacks(reclaim)
            handler.postDelayed(reclaim, 450)
        }
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacks(launch)
        handler.removeCallbacks(reclaim)
        if (registered) {
            runCatching { unregisterReceiver(receiver) }
            registered = false
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun schedule() {
        handler.removeCallbacks(launch)
        handler.postDelayed(launch, 800)
    }
}
