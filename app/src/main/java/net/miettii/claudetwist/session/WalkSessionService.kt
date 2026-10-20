package net.miettii.claudetwist.session

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.miettii.claudetwist.MainActivity
import net.miettii.claudetwist.R
import net.miettii.claudetwist.composer.ClaudeAppDriverService
import net.miettii.claudetwist.data.SettingsRepository
import net.miettii.claudetwist.feedback.Cue
import net.miettii.claudetwist.feedback.CueSounds
import net.miettii.claudetwist.gesture.TwistSensitivity
import net.miettii.claudetwist.gesture.TwistSensor
import net.miettii.claudetwist.notify.Notifier

// A walk: the gyroscope listens, the screen stays on, and every recognised twist is handed to
// [ClaudeAppDriverService]. The foreground service is what earns the app its sensor samples with the
// screen dark, and the wake lock is what keeps the CPU awake to deliver them on a phone whose
// gyroscope cannot wake it by itself. Both end with the session, which the holder stops from the
// notification, or which stops itself after [SESSION_LIMIT_MILLIS] so a forgotten session cannot
// drain the phone for a day.
class WalkSessionService : LifecycleService() {

    private val cues = CueSounds()
    private val screen by lazy { ScreenAwakeOverlay(this) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
    }
    // Built only once the chosen sensitivity has been read: that choice is the gesture's shape.
    private var twists: TwistSensor? = null
    private var hasStarted = false
    private var listeningSensitivity: TwistSensitivity? = null

    private val notificationActions = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP_SESSION -> stop(context)
                ACTION_DISCARD_DICTATION -> ClaudeAppDriverService.connectedInstance?.discardDictation()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            notificationActions,
            IntentFilter().apply {
                addAction(ACTION_STOP_SESSION)
                addAction(ACTION_DISCARD_DICTATION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        // A redelivered start must not raise a second overlay or schedule a second limit.
        if (hasStarted) return START_STICKY
        hasStarted = true
        if (!wakeLock.isHeld) wakeLock.acquire(SESSION_LIMIT_MILLIS)
        lifecycleScope.launch {
            val repository = SettingsRepository(this@WalkSessionService)
            screen.raise(shieldTouches = repository.readSettings().shieldTouchesDuringSession)
            cues.play(Cue.SessionStarted)
            // The gesture may be retuned mid-walk, so the listener follows the setting rather than
            // staying as it was when the session started.
            repository.settings.collect { live ->
                activeSession = ActiveSession(divertsDialAttempts = live.divertDialAttemptsDuringSession)
                if (live.twistSensitivity != listeningSensitivity) {
                    listeningSensitivity = live.twistSensitivity
                    listenForTwists(live.twistSensitivity)
                }
            }
        }
        lifecycleScope.launch {
            delay(SESSION_LIMIT_MILLIS)
            stop(this@WalkSessionService)
        }
        return START_STICKY
    }

    /** Listen for the gesture the holder chose, in place of any listener an earlier start left. */
    private fun listenForTwists(sensitivity: TwistSensitivity) {
        twists?.stopListening()
        twists = TwistSensor(this, sensitivity) {
            ClaudeAppDriverService.connectedInstance?.driveOneStep() ?: cues.play(Cue.DriveFailed)
        }.also(TwistSensor::startListening)
    }

    private fun startInForeground() {
        val notification: Notification = NotificationCompat.Builder(this, Notifier.SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.session_ongoing_title))
            .setContentText(getString(R.string.session_ongoing_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(0, getString(R.string.session_action_discard), broadcast(ACTION_DISCARD_DICTATION))
            .addAction(0, getString(R.string.session_action_stop), broadcast(ACTION_STOP_SESSION))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ONGOING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        this,
        action.hashCode(),
        Intent(action).setPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        activeSession = null
        twists?.stopListening()
        screen.lower()
        if (wakeLock.isHeld) wakeLock.release()
        unregisterReceiver(notificationActions)
        cues.play(Cue.SessionStopped)
        cues.release()
        super.onDestroy()
    }

    // What a session is, to the triggers that need to know one is under way. Null when none is.
    data class ActiveSession(val divertsDialAttempts: Boolean)

    companion object {
        // Owned by the running service: set when a session starts, cleared when it ends.
        @Volatile
        var activeSession: ActiveSession? = null

        private const val ONGOING_NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "claudetwist:session"
        // Longer than any walk, short enough that a session left running is not a dead battery.
        private const val SESSION_LIMIT_MILLIS = 4 * 60 * 60_000L
        private const val ACTION_STOP_SESSION = "net.miettii.claudetwist.STOP_SESSION"
        private const val ACTION_DISCARD_DICTATION = "net.miettii.claudetwist.DISCARD_DICTATION"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WalkSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalkSessionService::class.java))
        }
    }
}
