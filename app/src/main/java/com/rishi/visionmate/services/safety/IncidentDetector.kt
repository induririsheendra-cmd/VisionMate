package com.rishi.visionmate.services.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class IncidentDetector(
    context: Context,
    private val onPotentialIncidentDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isMonitoring = false
    private var lastIncidentTimestamp = 0L

    fun startMonitoring() {
        if (accelerometer != null && !isMonitoring) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            isMonitoring = true
            Log.d("IncidentDetector", "Sensor monitoring started for potential incident detection")
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            sensorManager?.unregisterListener(this)
            isMonitoring = false
            Log.d("IncidentDetector", "Sensor monitoring stopped")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total G-force magnitude
        val accelerationMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        // Threshold spike (approx 2.5G ~ 25 m/s²) with 10-second debounce
        val now = System.currentTimeMillis()
        if (accelerationMagnitude > 25.0f && (now - lastIncidentTimestamp > 10_000)) {
            lastIncidentTimestamp = now
            Log.w("IncidentDetector", "Potential incident anomaly detected! Acceleration: $accelerationMagnitude m/s²")
            onPotentialIncidentDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
