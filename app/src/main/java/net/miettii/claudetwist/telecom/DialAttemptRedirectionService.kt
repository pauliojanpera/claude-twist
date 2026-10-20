package net.miettii.claudetwist.telecom

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import net.miettii.claudetwist.composer.ClaudeAppDriverService
import net.miettii.claudetwist.feedback.Cue
import net.miettii.claudetwist.feedback.CueSounds
import net.miettii.claudetwist.session.WalkSessionService

// Drives the loop from a headset button, by way of the call it tries to place.
//
// A Bluetooth headset's button never reaches the input pipeline, and its media buttons are delivered
// to whichever app holds the media button session — the Claude app, while it narrates. The one press
// that escapes both is the one the headset sends as HFP's redial (AT+BLDN): the Bluetooth stack turns
// it into an ordinary outgoing call, and an outgoing call is something an app may answer for, through
// this service, once the holder has granted it the call redirection role.
//
// So a dial attempt made during a session is read as a trigger: the call is cancelled and the loop
// steps instead. Outside a session every call is placed as asked, so redial keeps working.
//
// What this costs is stated plainly in the app and in the README: if this app is not there to answer
// — killed, role revoked, service unbound, or simply too slow — Telecom places the call, and the
// headset button rings the last number dialled from a pocket. Hence [mayDivert], where every doubt
// resolves to placing the call: an emergency number is never diverted whatever the platform
// promises, and a number this app cannot vet is not diverted either.
class DialAttemptRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean,
    ) {
        // Telecom waits for exactly one of the three answers and places the call itself if none
        // arrives in time, so the answer is given before anything slower is attempted.
        if (!mayDivert(handle)) {
            placeCallUnmodified()
            return
        }
        cancelCall()
        driveOrComplain()
    }

    /**
     * Whether this dial attempt is a headset button rather than a call to make.
     *
     * Reads only values already in memory: a session that is under way says so in
     * [WalkSessionService.activeSession], and nothing here waits on storage, because Telecom is
     * waiting on this method.
     */
    private fun mayDivert(handle: Uri): Boolean {
        if (WalkSessionService.activeSession?.divertsDialAttempts != true) return false
        if (handle.scheme != PhoneAccount.SCHEME_TEL) return false
        val number = handle.schemeSpecificPart?.takeIf(String::isNotBlank) ?: return false
        return isKnownNotToBeEmergency(number)
    }

    /**
     * True only when the number is known to be an ordinary one. An unreadable number, a missing
     * permission or a failed check all answer false, so the call goes through: placing a call that
     * was meant as a button is a nuisance, and cancelling one that was meant as a call is not.
     */
    private fun isKnownNotToBeEmergency(number: String): Boolean = runCatching {
        !getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
    }.getOrDefault(false)

    private fun driveOrComplain() {
        val driver = ClaudeAppDriverService.connectedInstance
        if (driver != null) {
            driver.driveOneStep()
            return
        }
        // The accessibility service is off, so the cancelled call bought nothing. Say so audibly —
        // a pocket has no other way to learn it.
        CueSounds().run {
            play(Cue.DriveFailed)
            release()
        }
    }
}
