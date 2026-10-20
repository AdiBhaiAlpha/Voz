package com.example.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import com.example.data.preferences.UserPreferencesRepository

/** Opt-in, debounced, active calls only; never takes audio away from headsets. */
class ProximityAudioRouter(context: Context) : SensorEventListener {
    private val prefs = UserPreferencesRepository(context)
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun start() {
        sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    fun stop() { sensors.unregisterListener(this); pending?.let { handler.removeCallbacks(it) } }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        pending?.let { handler.removeCallbacks(it) }
        if (!prefs.loadPreferences().autoSpeakerEnabled) return
        val near = event.values[0] < minOf(event.sensor.maximumRange, 5f)
        val task = Runnable {
            if (CallSessionController.activeCalls.value.none { it.state == CallLifecycleState.ACTIVE }) return@Runnable
            val audio = CallSessionController.audioState.value
            if (audio.route != CallAudioState.ROUTE_EARPIECE && audio.route != CallAudioState.ROUTE_SPEAKER) return@Runnable
            val target = if (near) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
            if (audio.supportedRouteMask and target != 0 && audio.route != target) {
                CallSessionController.setAutomaticAudioRoute(target)
            }
        }
        pending = task
        handler.postDelayed(task, 600)
    }
}
