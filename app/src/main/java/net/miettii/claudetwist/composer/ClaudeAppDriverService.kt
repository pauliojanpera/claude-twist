package net.miettii.claudetwist.composer

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.Rect
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.miettii.claudetwist.data.SettingsRepository
import net.miettii.claudetwist.feedback.Cue
import net.miettii.claudetwist.feedback.CueSounds

// Presses the Claude app's controls on the holder's behalf. The session hands it a twist; it reads
// what the Claude app is showing and presses what that twist means there ([planPressesForTwist]).
//
// It reads the tree on demand rather than reacting to accessibility events: a chat redraws
// constantly, and a session that walked an hour of those events for nothing would cost battery to no
// end. The only thing it watches for is a response finishing, and only in the window after a send.
class ClaudeAppDriverService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cues = CueSounds()

    @Volatile private var search = AccessibilityTreeSearch(ClaudeAppControl.defaultDescriptions())
    @Volatile private var targetPackageName = DEFAULT_TARGET_PACKAGE_NAME

    private var pressSequence: Job? = null
    private var narrationWatch: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedInstance = this
        scope.launch {
            SettingsRepository(this@ClaudeAppDriverService).settings.collect { settings ->
                search = AccessibilityTreeSearch(settings.controlDescriptions)
                targetPackageName = settings.targetPackageName
            }
        }
    }

    override fun onDestroy() {
        connectedInstance = null
        cues.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Drive one step of the loop, whichever trigger asked. A trigger while a response is still
     * awaited abandons that wait: the holder has decided to speak again rather than hear the answer
     * out.
     */
    fun driveOneStep() {
        narrationWatch?.cancel()
        if (pressSequence?.isActive == true) return
        pressSequence = scope.launch { pressPlan(planPressesForTrigger(readClaudeApp())) }
    }

    /** Throw away what was spoken instead of sending it, for a trigger the pocket fired by itself. */
    fun discardDictation() {
        narrationWatch?.cancel()
        pressSequence?.cancel()
        pressSequence = scope.launch { pressPlan(planPressesForDiscard(readClaudeApp())) }
    }

    private suspend fun pressPlan(plan: List<ClaudeAppControl>) {
        if (plan.isEmpty()) return cues.play(Cue.NothingToDo)
        // Each press changes the screen, and the control a later step needs does not exist until the
        // earlier one has taken effect, so every step waits for its own control to appear.
        if (!plan.all { pressOnceOnScreen(it) }) return cues.play(Cue.DriveFailed)
        if (ClaudeAppControl.SendDictation in plan) {
            cues.play(Cue.DictationSent)
            narrationWatch = scope.launch { narrateResponseOnceFinished() }
        } else {
            cues.play(Cue.ListeningStarted)
        }
    }

    /**
     * Read the newest response aloud once it has finished. The action row carrying the narration
     * control appears when a message is complete, but this app does not trust the Claude app to
     * withhold that row while text is still streaming in: a response counts as finished only once
     * the text on screen has stopped changing between two readings a settling delay apart.
     */
    private suspend fun narrateResponseOnceFinished() {
        val narrated = withTimeoutOrNull(RESPONSE_WAIT_TIMEOUT_MILLIS) {
            var previousTextLength = -1
            var pressed = false
            while (!pressed) {
                delay(RESPONSE_SETTLE_MILLIS)
                val root = rootOfTargetApp() ?: continue
                val textLength = search.measureTextOnScreen(root)
                val hasSettled = textLength == previousTextLength
                previousTextLength = textLength
                if (!hasSettled) continue
                if (!search.readVisibleControls(root).offersCompletedResponse) continue
                pressed = pressOnceOnScreen(ClaudeAppControl.NarrateResponse)
                if (!pressed) break
            }
            pressed
        }
        cues.play(if (narrated == true) Cue.NarrationStarted else Cue.DriveFailed)
    }

    /** Wait for [control] to come on screen, then press it; false when it never appears. */
    private suspend fun pressOnceOnScreen(control: ClaudeAppControl): Boolean {
        val target = withTimeoutOrNull(CONTROL_APPEARANCE_TIMEOUT_MILLIS) {
            var found = findPressTargetFor(control)
            while (found == null) {
                delay(TREE_POLL_INTERVAL_MILLIS)
                found = findPressTargetFor(control)
            }
            found
        } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tapCentreOf(target)
    }

    private fun findPressTargetFor(control: ClaudeAppControl): AccessibilityNodeInfo? =
        rootOfTargetApp()?.let { search.findPressTargetFor(control, it) }

    private fun readClaudeApp(): ClaudeAppReading =
        rootOfTargetApp()?.let(search::readVisibleControls) ?: ClaudeAppReading.NOTHING_VISIBLE

    /** The window in front, but only while it belongs to the Claude app. */
    private fun rootOfTargetApp(): AccessibilityNodeInfo? =
        rootInActiveWindow?.takeIf { it.packageName?.toString() == targetPackageName }

    /**
     * Tap the centre of [node], for a control whose tree offers no clickable node to act on. A
     * gesture is the weaker way — it lands wherever the control was a moment ago — so it is only
     * ever the fallback.
     */
    private fun tapCentreOf(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty) return false
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) },
            0L,
            TAP_DURATION_MILLIS,
        )
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** Every content description the window in front publishes, for the Controls screen to list. */
    fun collectDescriptionsInFront(): List<String> =
        rootInActiveWindow?.let(search::collectDescriptionsOnScreen).orEmpty()

    companion object {
        // Set while the holder has the service enabled in the system's accessibility settings; the
        // session reaches the driver through this, and its absence is what the UI reports.
        @Volatile
        var connectedInstance: ClaudeAppDriverService? = null
            private set

        private const val TREE_POLL_INTERVAL_MILLIS = 120L
        // Long enough for the Claude app to swap the composer's buttons after a press, short enough
        // that a control which will never come still fails while the holder remembers twisting.
        private const val CONTROL_APPEARANCE_TIMEOUT_MILLIS = 4_000L
        private const val RESPONSE_SETTLE_MILLIS = 1_200L
        private const val RESPONSE_WAIT_TIMEOUT_MILLIS = 10 * 60_000L
        private const val TAP_DURATION_MILLIS = 60L
    }
}
