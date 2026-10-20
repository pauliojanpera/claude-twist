package net.miettii.claudetwist.composer

// A control of the Claude Android app that this app reads or presses, named by the content
// description the app publishes for accessibility services.
//
// Two shapes occur in the tree, and [AccessibilityTreeSearch] handles both: the description sits on
// the clickable node itself (the dictation buttons, which are Buttons), or on a presentational
// descendant whose nearest clickable ancestor is what must be pressed (the composer icons and the
// per-message action rows, which are Compose Views).
//
// [RetryMessage] is never pressed. It is read as a landmark: only the newest assistant message
// carries it in its action row, which is what tells [NarrateResponse] apart from the same control on
// an older message, and what marks the message as complete.
//
// The descriptions below were read off com.anthropic.claude 1.261016.51. A build of the Claude app
// that renames one is accommodated without a new release of this app: the holder relearns it in the
// Controls screen, and the override is stored per control (see SettingsRepository).
enum class ClaudeAppControl(val defaultDescription: String) {
    StartSpeechInput("Start speech input"),
    StopDictation("Stop dictation"),
    SendDictation("Send dictation"),
    CancelDictation("Cancel"),
    NarrateResponse("Narrate"),
    PauseNarration("Pause narration"),
    RetryMessage("Retry message"),
    ;

    companion object {
        /** The default vocabulary, as published by the Claude app version named above. */
        fun defaultDescriptions(): Map<ClaudeAppControl, String> =
            entries.associateWith { it.defaultDescription }
    }
}

/** The Claude app's package, overridable because a renamed or regional build would move it. */
const val DEFAULT_TARGET_PACKAGE_NAME = "com.anthropic.claude"
