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
// off com.anthropic.claude 1.261016.51 — see ClaudeAppControl. Every trigger shares these rules, so
// they are stated once here and hold for the twist and the headset button alike.
class ClaudeAppReadingTest {

    private val composerIdle = ClaudeAppReading(setOf(StartSpeechInput, NarrateResponse, RetryMessage))
    private val dictating = ClaudeAppReading(setOf(StopDictation, SendDictation, CancelDictation))
    private val narrating = ClaudeAppReading(setOf(StartSpeechInput, PauseNarration, RetryMessage))
    private val claudeAppNotInFront = ClaudeAppReading.NOTHING_VISIBLE

    @Test
    fun aTriggerOnTheIdleComposerStartsSpeaking() {
        assertEquals(listOf(StartSpeechInput), planPressesForTrigger(composerIdle))
    }

    @Test
    fun aTriggerWhileDictatingSendsWhatWasSpoken() {
        assertEquals(listOf(SendDictation), planPressesForTrigger(dictating))
    }

    @Test
    fun aTriggerWhileNarratingBreaksOffAndStartsTheNextTurn() {
        assertEquals(listOf(PauseNarration, StartSpeechInput), planPressesForTrigger(narrating))
    }

    @Test
    fun aTriggerWithTheClaudeAppOutOfFrontPressesNothing() {
        assertTrue(planPressesForTrigger(claudeAppNotInFront).isEmpty())
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
