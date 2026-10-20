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
    private val twists by lazy {
        TwistSensor(this) {
            ClaudeAppDriverService.connectedInstance?.respondToTwist() ?: cues.play(Cue.DriveFailed)
        }
    }

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
        lifecycleScope.launch {
            val settings = SettingsRepository(this@WalkSessionService).readSettings()
            screen.raise(shieldTouches = settings.shieldTouchesDuringSession)
        }
        if (!wakeLock.isHeld) wakeLock.acquire(SESSION_LIMIT_MILLIS)
        twists.startListening()
        cues.play(Cue.SessionStarted)
        lifecycleScope.launch {
            delay(SESSION_LIMIT_MILLIS)
            stop(this@WalkSessionService)
        }
        return START_STICKY
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
        twists.stopListening()
        screen.lower()
        if (wakeLock.isHeld) wakeLock.release()
        unregisterReceiver(notificationActions)
        cues.play(Cue.SessionStopped)
        cues.release()
        super.onDestroy()
    }

    companion object {
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
