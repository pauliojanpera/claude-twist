package net.miettii.claudetwist

import android.app.Application
import net.miettii.claudetwist.notify.Notifier

class ClaudeTwistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The session posts its ongoing notification the moment it starts, which a channel must
        // already exist for.
        Notifier.createChannels(this)
    }
}
