package com.android.launcher3.notification

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log

class NotificationListenerWatchdog(
    context: Context,
    private val isConnected: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var component: ComponentName? = null
    private var lastAttemptAt = 0L
    private var attempts = 0
    private var pending = false

    /** Call from Launcher.onResume(). Cheap when healthy. */
    fun check() {
        val cn = resolveListener()
        val nm = appContext.getSystemService(NotificationManager::class.java)
        val granted = if (cn != null && nm != null) nm.isNotificationListenerAccessGranted(cn) else false
        Log.d(TAG, "check() called: pending=$pending, cn=$cn, granted=$granted, connected=${isConnected()}")
        if (pending) return
        if (cn == null) return
        if (!granted) return
        if (isConnected()) {
            attempts = 0
            return
        }

        val now = SystemClock.elapsedRealtime()
        val sinceStart = now - Process.getStartElapsedRealtime()
        if (sinceStart < COLD_START_GRACE_MS) {
            val delay = COLD_START_GRACE_MS - sinceStart + 500
            Log.d(TAG, "Cold start grace active (${sinceStart}ms < ${COLD_START_GRACE_MS}ms); scheduling check in ${delay}ms")
            schedule(delay) { check() }
            return
        }
        if (attempts >= MAX_ATTEMPTS || now - lastAttemptAt < COOLDOWN_MS) return

        attempts++
        lastAttemptAt = now
        Log.i(TAG, "Notification listener disconnected; rebind attempt $attempts for $cn")
        NotificationListenerService.requestRebind(cn)           // first line
        schedule(VERIFY_DELAY_MS) {
            if (!isConnected()) {
                Log.i(TAG, "Notification listener still disconnected; toggling component $cn")
                toggle(cn) {                                    // second line with settle delay
                    Log.i(TAG, if (isConnected()) "Notification listener recovered" else "Notification listener recovery gave up for now")
                }
            }
        }
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        pending = false
    }

    private fun schedule(delayMs: Long, block: () -> Unit) {
        pending = true
        handler.postDelayed({
            pending = false
            block()
        }, delayMs)
    }

    private fun toggle(cn: ComponentName, onDone: () -> Unit) {
        val pm = appContext.packageManager
        try {
            Log.i(TAG, "Toggling component $cn: disabling")
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            // Allow system server time to process unbind before re-enabling
            schedule(TOGGLE_SETTLE_DELAY_MS) {
                try {
                    Log.i(TAG, "Toggling component $cn: re-enabling")
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to re-enable component $cn", e)
                }
                schedule(VERIFY_DELAY_MS) {
                    onDone()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "toggle failed", e)
            onDone()
        }
    }

    private fun resolveListener(): ComponentName? {
        component?.let { return it }
        val nm = appContext.getSystemService(NotificationManager::class.java)
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE)
            .setPackage(appContext.packageName)
        val candidates = appContext.packageManager
            .queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            .mapNotNull { it.serviceInfo }
            .filter { it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE }
            .map { ComponentName(it.packageName, it.name) }
        Log.d(TAG, "resolveListener candidates: $candidates")

        val granted = if (nm != null) candidates.firstOrNull { nm.isNotificationListenerAccessGranted(it) } else null
        val resolved = granted ?: candidates.firstOrNull()
        if (granted != null) {
            component = granted
        }
        return resolved
    }

    private companion object {
        const val TAG = "NLWatchdog"
        const val COLD_START_GRACE_MS = 10_000L
        const val VERIFY_DELAY_MS = 3_000L
        const val TOGGLE_SETTLE_DELAY_MS = 500L
        const val COOLDOWN_MS = 60_000L
        const val MAX_ATTEMPTS = 3
    }
}
