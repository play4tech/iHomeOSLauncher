/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.View
import java.lang.reflect.Field
import java.util.LinkedHashSet
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Intercepts and controls the liquid glass dynamic specular highlight, locking it permanently
 * to the portrait lighting coordinate system.
 *
 * Prevents specular highlight from swinging, jumping, or inverting when the phone is rotated
 * (e.g. roll, steering wheel rotation, or rotating into landscape orientation), while preserving
 * dynamic specular glint when the device is tilted in portrait.
 */
object PortraitSensorHighlightController : SensorEventListener {

    private const val TAG = "PortraitHighlight"
    private const val SMOOTHING = 0.14f
    private const val NOTIFY_EPSILON = 0.003f

    // Reflection handles to LightSourceController internal state
    private var controllerInstance: SensorEventListener? = null
    private var lightDirXField: Field? = null
    private var lightDirYField: Field? = null
    private var registeredField: Field? = null
    private var listenersField: Field? = null
    private var sensorManagerField: Field? = null
    private var reflectionInitialized = false

    private var sensorManager: SensorManager? = null
    private var isListening = false

    private var currentLightX = 0.866f
    private var currentLightY = 0.5f
    private var lastNotifiedX = 0.866f
    private var lastNotifiedY = 0.5f

    private fun initReflection() {
        if (reflectionInitialized) return
        try {
            val cls = Class.forName("com.example.liquidglass.LightSourceController")
            val instanceField = cls.getDeclaredField("INSTANCE").apply { isAccessible = true }
            controllerInstance = instanceField.get(null) as? SensorEventListener
            lightDirXField = cls.getDeclaredField("lightDirX").apply { isAccessible = true }
            lightDirYField = cls.getDeclaredField("lightDirY").apply { isAccessible = true }
            registeredField = cls.getDeclaredField("registered").apply { isAccessible = true }
            listenersField = cls.getDeclaredField("listeners").apply { isAccessible = true }
            sensorManagerField = cls.getDeclaredField("sensorManager").apply { isAccessible = true }
            reflectionInitialized = true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize reflection on LightSourceController", e)
        }
    }

    private fun suppressLibraryListener() {
        try {
            val inst = controllerInstance
            if (inst != null && sensorManager != null) {
                sensorManager?.unregisterListener(inst)
            }
            registeredField?.setBoolean(null, true)
            sensorManagerField?.set(null, sensorManager)
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun ensureStarted(context: Context) {
        initReflection()
        val appContext = context.applicationContext
        if (sensorManager == null) {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }

        // Suppress the library's unconstrained LightSourceController listener
        suppressLibraryListener()

        if (!isListening) {
            val sm = sensorManager ?: return
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: return

            val success = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (success) {
                isListening = true
                Log.d(TAG, "Registered portrait-locked sensor highlight listener")
            }
        }
    }

    @JvmStatic
    fun stopIfNoViews() {
        if (!isListening) return
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        isListening = false
        Log.d(TAG, "Unregistered portrait-locked sensor highlight listener")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return

        // Keep library listener suppressed
        suppressLibraryListener()

        val ax = event.values[0]
        val ay = if (event.values.size > 1) event.values[1] else 0f

        // In 3D physics, the total tilt of the phone relative to the horizontal table
        // is given by the magnitude of gravity projected onto the screen plane:
        // gPlane = sqrt(ax^2 + ay^2).
        // Crucially, gPlane is mathematically invariant under phone rotation in the screen plane
        // (ax^2 + ay^2 = gPlane^2 regardless of rotation angle).
        // Therefore, rotating the phone does not change the tilt intensity.
        val gPlane = sqrt(ax * ax + ay * ay)
        val tilt = (gPlane / 9.80665f).coerceIn(0f, 1f)

        // Baseline diagonal specular light in portrait: (0.866, 0.5) ~ 30 degrees (Apple iOS aesthetic)
        // As portrait pitch increases (held upright towards user):
        // Light subtly moves towards the top rim (0.39, 0.93) without ever swinging horizontally.
        val targetX = 0.866f * (1f - tilt * 0.55f)
        val targetY = 0.5f + 0.5f * tilt

        val mag = sqrt(targetX * targetX + targetY * targetY)
        val normX = if (mag > 1e-4f) targetX / mag else 0.866f
        val normY = if (mag > 1e-4f) targetY / mag else 0.5f

        // Exponential smoothing (0.14f matches library response curve)
        currentLightX += (normX - currentLightX) * SMOOTHING
        currentLightY += (normY - currentLightY) * SMOOTHING

        val curMag = sqrt(currentLightX * currentLightX + currentLightY * currentLightY)
        val finalX = if (curMag > 1e-4f) currentLightX / curMag else 0.866f
        val finalY = if (curMag > 1e-4f) currentLightY / curMag else 0.5f

        currentLightX = finalX
        currentLightY = finalY

        // Update LightSourceController static fields for all LiquidGlassViews
        try {
            lightDirXField?.setFloat(null, finalX)
            lightDirYField?.setFloat(null, finalY)
        } catch (_: Throwable) {}

        // Invalidate views if light moved by more than epsilon
        if (abs(finalX - lastNotifiedX) > NOTIFY_EPSILON || abs(finalY - lastNotifiedY) > NOTIFY_EPSILON) {
            lastNotifiedX = finalX
            lastNotifiedY = finalY

            @Suppress("UNCHECKED_CAST")
            val listeners = try {
                listenersField?.get(null) as? LinkedHashSet<View>
            } catch (_: Throwable) { null }

            if (listeners != null) {
                val viewsToNotify: List<View> = synchronized(listeners) {
                    listeners.toList()
                }
                for (v in viewsToNotify) {
                    v.postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
