package net.miettii.claudetwist.composer

import net.miettii.claudetwist.composer.ClaudeAppControl.CancelDictation
import net.miettii.claudetwist.composer.ClaudeAppControl.NarrateResponse
import net.miettii.claudetwist.composer.ClaudeAppControl.PauseNarration
import net.miettii.claudetwist.composer.ClaudeAppControl.RetryMessage
import net.miettii.claudetwist.composer.ClaudeAppControl.SendDictation
import net.miettii.claudetwist.composer.ClaudeAppControl.StartSpeechInput
import net.miettii.claudetwist.composer.ClaudeAppControl.StopDictation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The readings below are the control sets the Claude app actually publishes in each state, as read
// off com.anthropic.claude 1.261016.51 — see ClaudeAppControl.
class ClaudeAppReadingTest {

    private val composerIdle = ClaudeAppReading(setOf(StartSpeechInput, NarrateResponse, RetryMessage))
    private val dictating = ClaudeAppReading(setOf(StopDictation, SendDictation, CancelDictation))
    private val narrating = ClaudeAppReading(setOf(StartSpeechInput, PauseNarration, RetryMessage))
    private val claudeAppNotInFront = ClaudeAppReading.NOTHING_VISIBLE

    @Test
    fun aTwistOnTheIdleComposerStartsSpeaking() {
        assertEquals(listOf(StartSpeechInput), planPressesForTwist(composerIdle))
    }

    @Test
    fun aTwistWhileDictatingSendsWhatWasSpoken() {
        assertEquals(listOf(SendDictation), planPressesForTwist(dictating))
    }

    @Test
    fun aTwistWhileNarratingBreaksOffAndStartsTheNextTurn() {
        assertEquals(listOf(PauseNarration, StartSpeechInput), planPressesForTwist(narrating))
    }

    @Test
    fun aTwistWithTheClaudeAppOutOfFrontPressesNothing() {
        assertTrue(planPressesForTwist(claudeAppNotInFront).isEmpty())
    }

    @Test
    fun onlyDictationCanBeDiscarded() {
        assertEquals(listOf(CancelDictation), planPressesForDiscard(dictating))
        assertTrue(planPressesForDiscard(composerIdle).isEmpty())
        assertTrue(planPressesForDiscard(narrating).isEmpty())
    }

    @Test
    fun dictatingIsNotMistakenForAnIdleComposer() {
        assertTrue(dictating.isDictating)
        assertFalse(dictating.acceptsSpeechInput)
        assertFalse(composerIdle.isDictating)
    }

    @Test
    fun aResponseIsOfferedOnlyWithTheNewestMessageSActionRowOnScreen() {
        assertTrue(composerIdle.offersCompletedResponse)
        // Scrolled so that only an older message's action row is in the tree: it carries the
        // narration control but not the landmark that marks the newest message.
        assertFalse(ClaudeAppReading(setOf(StartSpeechInput, NarrateResponse)).offersCompletedResponse)
        // Still streaming in: no action row at all yet.
        assertFalse(ClaudeAppReading(setOf(StartSpeechInput)).offersCompletedResponse)
    }

    @Test
    fun narrationIsRecognisedWhileItPlays() {
        assertTrue(narrating.isNarrating)
        assertFalse(composerIdle.isNarrating)
    }
}
