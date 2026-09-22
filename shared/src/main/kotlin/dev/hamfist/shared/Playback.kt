package dev.hamfist.shared

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.*
import dev.hamfist.core.Morse
import dev.hamfist.core.Pattern
import dev.hamfist.core.DeferredDelivery
import java.time.LocalTime

object Playback {
    private var busyUntil = 0L
    // A blocked UI must not delay delivery or hold the CPU awake. This thread sleeps between
    // one-shot messages; there is no polling, periodic work, or foreground service.
    private val handler by lazy {
        Handler(HandlerThread("hamfist-playback",Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper)
    }
    private var pending: Runnable? = null
    private var handoffLock: PowerManager.WakeLock? = null
    private fun vibrator(context: Context): Vibrator = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    fun suppression(context: Context, settings: Settings): String? {
        if (!settings.enabled) return "Hamfist is paused"
        val now = LocalTime.now()
        if (settings.quiet.contains(now.hour * 60 + now.minute)) return "Quiet hours"
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return "Do Not Disturb"
        if (settings.respectSilent && context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_SILENT) return "Silent mode"
        if (settings.batterySaver && context.getSystemService(PowerManager::class.java).isPowerSaveMode) return "Battery Saver"
        if (settings.lowBatteryPercent > 0) {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            if (level >= 0 && scale > 0 && plugged == 0 && level * 100 / scale <= settings.lowBatteryPercent) return "Low battery"
        }
        return null
    }

    fun pattern(text: String, settings: Settings): Pattern = Morse.encode(text, settings.timing, settings.maxChars, settings.maxSeconds * 1000L)

    @Synchronized fun play(context: Context, text: String, preview: Boolean = false, sourceApp: String? = null): String {
        val settings = SettingsStore.get(context).current
        if (context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return "Do Not Disturb"
        // Explicit previews bypass quiet hours / pause / battery gates, but never bypass system DND.
        if (!preview) suppression(context, settings)?.let { return it }
        if (!preview) {
            if (pending != null || SystemClock.elapsedRealtime() < busyUntil) return "Busy · skipped to avoid overlapping messages"
            val appContext=context.applicationContext
            // Let the source app's normal buzz finish first. Hold only the CPU during this one-shot
            // handoff, never the screen. Android's vibrator service owns the waveform thereafter.
            val delay=settings.startDelayMs.toLong()
            val request=DeferredDelivery(System.currentTimeMillis(),SystemClock.elapsedRealtime(),delay+1000) {
                val current=SettingsStore.get(appContext).current
                current.local && suppression(appContext,current)==null &&
                    (sourceApp==null || sourceApp in current.apps && AppCatalog.hasAccess(appContext))
            }
            val lock=appContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"hamfist:morse-handoff")
            lock.setReferenceCounted(false)
            lock.acquire(delay+1000)
            handoffLock=lock
            val work=Runnable {
                synchronized(this) {
                    try {
                        pending=null
                        // If execution is delayed past the handoff window, discard the signal.
                        // Never play it much later when the device next wakes.
                        if (lock.isHeld && request.canDeliver(SystemClock.elapsedRealtime())) playNow(appContext,text)
                    } finally { if(lock.isHeld) lock.release(); handoffLock=null }
                }
            }
            pending=work
            handler.postDelayed(work,delay)
            return "Morse scheduled"
        }
        stop(context)
        return playNow(context,text)
    }

    private fun playNow(context: Context, text: String): String {
        val settings=SettingsStore.get(context).current
        val pattern = pattern(text, settings)
        if (pattern.timings.isEmpty()) return "No supported Morse characters"
        val motor = vibrator(context)
        if (!motor.hasVibrator()) return "No vibration motor on this device"
        val now = SystemClock.elapsedRealtime()
        motor.cancel()
        val effect = VibrationEffect.createWaveform(pattern.timings, -1)
        // Wear's NotificationCollectorService reserves generic notification effects for System UI.
        // Morse is a tactile communication prompt; use the public attentional communication usage
        // on Wear, which is permitted in the background. Never request interruption-bypass flags.
        // Our explicit DND, silent, quiet-hours and power checks apply before this call.
        if (Build.VERSION.SDK_INT >= 33) {
            val usage = if (isWatch(context)) VibrationAttributes.USAGE_COMMUNICATION_REQUEST else VibrationAttributes.USAGE_NOTIFICATION
            motor.vibrate(effect, VibrationAttributes.Builder().setUsage(usage).build())
        } else {
            @Suppress("DEPRECATION")
            motor.vibrate(effect, AudioAttributes.Builder().setUsage(if (isWatch(context)) AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST else AudioAttributes.USAGE_NOTIFICATION).build())
        }
        busyUntil = now + pattern.durationMs
        return "Sent to vibration motor · ${pattern.text.length} characters"
    }

    @Synchronized fun stop(context: Context) {
        pending?.let(handler::removeCallbacks); pending=null
        handoffLock?.let { if(it.isHeld) it.release() }; handoffLock=null
        vibrator(context).cancel(); busyUntil = 0
    }
}
