package net.miettii.claudetwist.composer

import android.view.accessibility.AccessibilityNodeInfo

// Finds the Claude app's controls in an accessibility tree, under the content descriptions in
// [descriptions] — the defaults of [ClaudeAppControl], or whatever the holder relearned. Holds no
// state: every call walks the tree it is handed, so a reading is never stale.
class AccessibilityTreeSearch(private val descriptions: Map<ClaudeAppControl, String>) {

    /** Which of the controls are on screen, as the gesture rules want to see it. */
    fun readVisibleControls(root: AccessibilityNodeInfo): ClaudeAppReading {
        val onScreen = root.selfAndDescendants().mapNotNullTo(HashSet()) { node ->
            val description = node.contentDescription?.toString() ?: return@mapNotNullTo null
            descriptions.entries.firstOrNull { it.value == description }?.key
        }
        return ClaudeAppReading(onScreen)
    }

    /**
     * The node to press to activate [control], or null when the control is not on screen. The
     * narration control appears on every assistant message, so it resolves to the newest message's
     * alone; every other control is unique on screen.
     */
    fun findPressTargetFor(control: ClaudeAppControl, root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        if (control == ClaudeAppControl.NarrateResponse) {
            findNarrationTargetOnNewestResponse(root)
        } else {
            findByDescription(root, control)?.nearestClickableSelfOrAncestor()
        }

    /**
     * The narration control of the newest assistant message. Climbs from the [RetryMessage]
     * landmark — which only the newest message's action row carries — until the subtree reached also
     * holds the narration control, so the two are known to belong to the same row however deeply the
     * Claude app nests them.
     */
    private fun findNarrationTargetOnNewestResponse(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val landmark = findByDescription(root, ClaudeAppControl.RetryMessage) ?: return null
        val wanted = descriptions[ClaudeAppControl.NarrateResponse] ?: return null
        var row: AccessibilityNodeInfo? = landmark.parent
        repeat(ACTION_ROW_SEARCH_DEPTH) {
            val withinRow = row ?: return null
            withinRow.selfAndDescendants()
                .firstOrNull { it.contentDescription?.toString() == wanted }
                ?.let { return it.nearestClickableSelfOrAncestor() }
            row = withinRow.parent
        }
        return null
    }

    /** The first node carrying the description configured for [control], in document order. */
    private fun findByDescription(root: AccessibilityNodeInfo, control: ClaudeAppControl): AccessibilityNodeInfo? {
        val wanted = descriptions[control] ?: return null
        return root.selfAndDescendants().firstOrNull { it.contentDescription?.toString() == wanted }
    }

    /**
     * How much text the tree holds, as a cheap fingerprint of a response still streaming in. Two
     * equal readings a moment apart mean the Claude app has stopped writing.
     */
    fun measureTextOnScreen(root: AccessibilityNodeInfo): Int =
        root.selfAndDescendants().sumOf { (it.text?.length ?: 0) }

    /** Every content description on screen, for the Controls screen to show when one is renamed. */
    fun collectDescriptionsOnScreen(root: AccessibilityNodeInfo): List<String> =
        root.selfAndDescendants()
            .mapNotNull { it.contentDescription?.toString()?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()

    private companion object {
        // An action row nests its controls a couple of levels below a shared container; a handful of
        // levels is enough to reach it and shallow enough never to escape into a sibling message.
        const val ACTION_ROW_SEARCH_DEPTH = 5
    }
}

/** This node and every node below it, itself first, in document order. */
fun AccessibilityNodeInfo.selfAndDescendants(): Sequence<AccessibilityNodeInfo> = sequence {
    yield(this@selfAndDescendants)
    for (index in 0 until childCount) {
        val child = getChild(index) ?: continue
        yieldAll(child.selfAndDescendants())
    }
}

/**
 * The node that must be pressed to activate this one: itself when it is clickable, else its nearest
 * clickable ancestor. The Claude app's composer icons and action rows publish their description on a
 * presentational node and leave the click to an ancestor; its dictation buttons carry both.
 */
fun AccessibilityNodeInfo.nearestClickableSelfOrAncestor(): AccessibilityNodeInfo? {
    var candidate: AccessibilityNodeInfo? = this
    while (candidate != null) {
        if (candidate.isClickable) return candidate
        candidate = candidate.parent
    }
    return null
}
