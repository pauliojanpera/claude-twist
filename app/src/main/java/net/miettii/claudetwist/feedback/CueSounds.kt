package net.miettii.claudetwist.feedback

import android.media.AudioManager
import android.media.ToneGenerator

// What just happened, as a sound. The phone is in a pocket for the whole session, so every outcome
// has to be told apart by ear alone — which is why there are few cues and no two share a tone.
enum class Cue(val toneType: Int, val durationMillis: Int) {
    /** The Claude app is listening; start speaking. */
    ListeningStarted(ToneGenerator.TONE_PROP_BEEP, 120),

    /** What was spoken has been sent. */
    DictationSent(ToneGenerator.TONE_PROP_ACK, 200),

    /** The response is finished and is being read aloud. */
    NarrationStarted(ToneGenerator.TONE_PROP_PROMPT, 150),

    /** The twist meant nothing here: the Claude app is not in front, or shows no control to press. */
    NothingToDo(ToneGenerator.TONE_PROP_NACK, 200),

    /** A control that was expected to follow a press never appeared — the phone needs looking at. */
    DriveFailed(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400),

    SessionStarted(ToneGenerator.TONE_CDMA_CONFIRM, 250),
    SessionStopped(ToneGenerator.TONE_CDMA_PIP, 250),
}

// Plays the cues. The alarm stream carries them, so they are heard with the phone silenced — which
// is how a phone goes on a walk — at a fixed modest volume rather than an alarm's forced maximum.
class CueSounds {

    private val tones: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_ALARM, CUE_VOLUME_PERCENT) }.getOrNull()
    }

    fun play(cue: Cue) {
        runCatching { tones?.startTone(cue.toneType, cue.durationMillis) }
    }

    fun release() {
        runCatching { tones?.release() }
    }

    private companion object {
        const val CUE_VOLUME_PERCENT = 55
    }
}
