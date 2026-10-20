package net.miettii.claudetwist.gesture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

// Listens to the gyroscope for as long as a session runs and calls [onTwist] for each completed
// gesture ([TwistDetector]). The angular speed about the phone's long axis is the y component of a
// gyroscope sample. The gesture must work with the phone pocketed and its screen dark: sensors reach
// a background app only through a foreground service — the session is one — and with the screen off
// only while the CPU is awake, which the session holds a wake lock for. A wake-up gyroscope, where
// the phone has one, wakes the CPU for each sample by itself. Callbacks arrive on the main thread.
class TwistSensor(context: Context, private val onTwist: () -> Unit) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gyroscope: Sensor? = sensors?.let { pickGyroscope(it) }
    private val detector = TwistDetector()
    private var listening = false

    /** Whether the phone can recognise the gesture at all; without a gyroscope nothing can drive. */
    val isAvailable: Boolean get() = gyroscope != null

    fun startListening() {
        if (listening || gyroscope == null) return
        detector.forgetSwingsSoFar()
        listening = sensors.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopListening() {
        if (!listening) return
        sensors.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (detector.onSample(event.timestamp, event.values[1])) onTwist()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    companion object {
        fun hasGyroscope(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java)?.let { pickGyroscope(it) } != null

        private fun pickGyroscope(sensors: SensorManager): Sensor? =
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
                ?: sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
}
