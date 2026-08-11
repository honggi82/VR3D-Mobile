package io.github.honggi82.vr3dmobile.sensor

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import io.github.honggi82.vr3dmobile.domain.ViewGrid
import kotlin.math.atan2
import kotlin.math.sqrt

class TiltTracker(
    private val activity: Activity,
    private val onTilt: (pitch: Float, roll: Float) -> Unit,
) : SensorEventListener {
    private val sensorManager = activity.getSystemService(SensorManager::class.java)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val selectedSensor = rotationSensor ?: accelerometer
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private var rawPitch = 0f
    private var rawRoll = 0f
    private var basePitch = 0f
    private var baseRoll = 0f
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var hasReading = false

    val isAvailable: Boolean get() = selectedSensor != null

    fun start() {
        selectedSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun center() {
        if (!hasReading) return
        basePitch = rawPitch
        baseRoll = rawRoll
        smoothPitch = 0f
        smoothRoll = 0f
        onTilt(0f, 0f)
    }

    fun resetCalibration() {
        hasReading = false
        smoothPitch = 0f
        smoothRoll = 0f
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val reading = if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            rotationReading(event.values)
        } else {
            accelerometerReading(event.values)
        }
        rawPitch = reading.first
        rawRoll = reading.second
        if (!hasReading) {
            hasReading = true
            basePitch = rawPitch
            baseRoll = rawRoll
        }
        val targetPitch = ViewGrid.clampPitch(angleDelta(rawPitch, basePitch))
        val targetRoll = ViewGrid.clampRoll(angleDelta(rawRoll, baseRoll))
        smoothPitch += FILTER_ALPHA * (targetPitch - smoothPitch)
        smoothRoll += FILTER_ALPHA * (targetRoll - smoothRoll)
        onTilt(smoothPitch, smoothRoll)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun rotationReading(values: FloatArray): Pair<Float, Float> {
        SensorManager.getRotationMatrixFromVector(rotation, values)
        val (axisX, axisY) = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
        SensorManager.getOrientation(remapped, orientation)
        return Math.toDegrees(orientation[1].toDouble()).toFloat() to
            Math.toDegrees(orientation[2].toDouble()).toFloat()
    }

    private fun accelerometerReading(values: FloatArray): Pair<Float, Float> {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val pitch = Math.toDegrees(atan2(-y, sqrt(x * x + z * z)).toDouble()).toFloat()
        val roll = Math.toDegrees(atan2(x, sqrt(y * y + z * z)).toDouble()).toFloat()
        return pitch to roll
    }

    private fun angleDelta(value: Float, baseline: Float): Float {
        var delta = value - baseline
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    companion object {
        private const val FILTER_ALPHA = 0.16f
    }
}
