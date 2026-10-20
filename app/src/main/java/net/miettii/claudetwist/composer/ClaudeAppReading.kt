package net.miettii.claudetwist.composer

import net.miettii.claudetwist.composer.ClaudeAppControl.CancelDictation
import net.miettii.claudetwist.composer.ClaudeAppControl.NarrateResponse
import net.miettii.claudetwist.composer.ClaudeAppControl.PauseNarration
import net.miettii.claudetwist.composer.ClaudeAppControl.RetryMessage
import net.miettii.claudetwist.composer.ClaudeAppControl.SendDictation
import net.miettii.claudetwist.composer.ClaudeAppControl.StartSpeechInput

// Which controls the Claude app is showing right now. The app offers a control only in the state
// that control belongs to, so the set alone names the state unambiguously and this app keeps no
// state machine of its own: every press is decided from what is on screen at that instant, which is
// what keeps a missed press, a stray touch or a scroll from putting the two out of step.
@JvmInline
value class ClaudeAppReading(val visibleControls: Set<ClaudeAppControl>) {

    /** Speech input is running: the Claude app is listening and offers to stop or send it. */
    val isDictating: Boolean get() = SendDictation in visibleControls

    /** The newest response is being read aloud, so the control to stop it is offered in its place. */
    val isNarrating: Boolean get() = PauseNarration in visibleControls

    /** The composer is idle and will take speech input. */
    val acceptsSpeechInput: Boolean get() = StartSpeechInput in visibleControls

    /**
     * The newest assistant message has finished and can be read aloud. Its action row appears only
     * once the message is complete, and [RetryMessage] marks that row as the newest one's.
     */
    val offersCompletedResponse: Boolean
        get() = NarrateResponse in visibleControls && RetryMessage in visibleControls

    companion object {
        val NOTHING_VISIBLE = ClaudeAppReading(emptySet())
    }
}

/**
 * The controls to press, in order, when the holder twists the phone while [reading] holds — the
 * whole gesture vocabulary of this app, as one pure function.
 *
 * One gesture drives one step of the loop, and what that step is follows from the state the reading
 * names: start speaking, send what was spoken, or break off a narration to speak again. Pressing
 * the composer's microphone does not itself stop a narration, so leaving one takes two presses.
 */
fun planPressesForTwist(reading: ClaudeAppReading): List<ClaudeAppControl> = when {
    reading.isDictating -> listOf(SendDictation)
    reading.isNarrating -> listOf(PauseNarration, StartSpeechInput)
    reading.acceptsSpeechInput -> listOf(StartSpeechInput)
    else -> emptyList()
}

/**
 * The controls to press to abandon what was spoken instead of sending it. Offered as a second
 * gesture so a misfire in the pocket costs nothing; idle and narrating states have nothing to
 * abandon.
 */
fun planPressesForDiscard(reading: ClaudeAppReading): List<ClaudeAppControl> =
    if (reading.isDictating) listOf(CancelDictation) else emptyList()
