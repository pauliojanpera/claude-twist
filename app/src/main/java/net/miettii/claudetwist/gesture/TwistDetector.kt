package net.miettii.claudetwist.gesture

// Recognises the drive gesture in gyroscope samples: the phone twisted about its long axis back and
// forth twice — out, back, out, back — within [windowNanos]. A swing is an excursion of the angular
// speed about the long axis past [threshold] rad/s, and only a swing the other way from the last one
// counts, so a phone swinging on a walking or running leg (rotation about the other axes) or
// tumbling one way in a pocket drives nothing. A completed gesture is followed by [refractoryNanos]
// in which nothing counts, so one twist presses one control. Pure arithmetic on samples, free of
// Android types, so it is unit-tested on the JVM.
class TwistDetector(
    private val threshold: Float = 4f,
    private val swings: Int = 4,
    private val windowNanos: Long = 1_500_000_000L,
    private val refractoryNanos: Long = 1_000_000_000L,
) {
    // Start times of the swings counted so far, oldest first; they alternate in direction.
    private val swingStarts = ArrayDeque<Long>()
    // Direction of the last counted swing: 1, -1, or 0 when none is counted.
    private var lastSwing = 0
    // Direction of the excursion past the threshold under way: 1, -1, or 0 inside the threshold.
    private var excursion = 0
    private var refractoryUntil = Long.MIN_VALUE

    /** Take one sample of angular speed about the long axis; true when it completes the gesture. */
    fun onSample(timestampNanos: Long, rate: Float): Boolean {
        val direction = when {
            rate >= threshold -> 1
            rate <= -threshold -> -1
            else -> 0
        }
        val startsSwing = direction != 0 && direction != excursion
        excursion = direction
        if (!startsSwing || timestampNanos < refractoryUntil) return false

        while (swingStarts.isNotEmpty() && timestampNanos - swingStarts.first() > windowNanos) {
            swingStarts.removeFirst()
        }
        if (swingStarts.isEmpty()) lastSwing = 0
        if (direction == lastSwing) return false
        swingStarts.addLast(timestampNanos)
        lastSwing = direction
        if (swingStarts.size < swings) return false

        forgetSwingsSoFar()
        refractoryUntil = timestampNanos + refractoryNanos
        return true
    }

    /** Forget the swings counted so far, for listening afresh. */
    fun forgetSwingsSoFar() {
        swingStarts.clear()
        lastSwing = 0
        excursion = 0
    }
}
