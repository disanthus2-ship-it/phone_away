package com.phoneaway.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Watches the accelerometer for the phone being picked up.
 *
 * A phone lying on a table reads a total acceleration of ~9.81 m/s² (gravity
 * alone). Picking it up, or carrying it, pushes that reading away from 9.81.
 * We require the disturbance to persist for [SUSTAIN_MILLIS] before calling it
 * a break, so a passing truck or a door slam does not cost you an hour of work.
 */
class StillnessDetector(
    context: Context,
    private val onMoved: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** True when the device has no accelerometer, so callers can warn the user. */
    val isAvailable: Boolean get() = accelerometer != null

    private var disturbedSinceUptime: Long? = null
    private var running = false

    fun start() {
        val sensor = accelerometer ?: return
        if (running) return
        running = true
        disturbedSinceUptime = null
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!running) return
        running = false
        disturbedSinceUptime = null
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)

        if (deviation < MOVEMENT_THRESHOLD) {
            disturbedSinceUptime = null
            return
        }

        val now = SystemClock.elapsedRealtime()
        val since = disturbedSinceUptime
        if (since == null) {
            disturbedSinceUptime = now
        } else if (now - since >= SUSTAIN_MILLIS) {
            stop()
            onMoved()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** m/s² of deviation from gravity that counts as movement. */
        const val MOVEMENT_THRESHOLD = 1.5f

        /** How long the disturbance must last before the session breaks. */
        const val SUSTAIN_MILLIS = 1_200L
    }
}
