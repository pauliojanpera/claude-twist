package net.miettii.claudetwist.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import net.miettii.claudetwist.R

// Builds the notification channel the session's ongoing notification lives on. The notification
// itself is built by the session, which owns its actions; the channel is created once at startup
// because a foreground service may not post on a channel that does not exist yet.
object Notifier {

    const val SESSION_CHANNEL_ID = "session"

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SESSION_CHANNEL_ID,
                context.getString(R.string.channel_session_name),
                // Low: the cues are the session's voice, and a sound per state change would be noise.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_session_description) }
        )
    }
}
