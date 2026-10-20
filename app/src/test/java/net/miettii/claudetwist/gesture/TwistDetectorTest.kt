package net.miettii.claudetwist.gesture

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class TwistDetectorTest {

    // Samples at the rate SENSOR_DELAY_GAME delivers: every 20 ms.
    private val sampleNanos = 20_000_000L

    // Angular speed about the long axis while the phone is twisted out and back [twists] times,
    // each twist a sine period of [periodMs]: positive one way, negative the other.
    private fun twists(twists: Int, periodMs: Long, peak: Float): List<Float> {
        val samples = (twists * periodMs * 1_000_000L / sampleNanos).toInt()
        return List(samples) { index ->
            (peak * sin(2 * PI * index * sampleNanos / (periodMs * 1_000_000.0))).toFloat()
        }
    }

    private fun still(ms: Long): List<Float> = List((ms * 1_000_000L / sampleNanos).toInt()) { 0f }

    // How many gestures the detector recognises in the samples, fed one per sample period.
    private fun recognised(samples: List<Float>): Int {
        val detector = TwistDetector()
        return samples.withIndex().count { (index, rate) -> detector.onSample(index * sampleNanos, rate) }
    }

    @Test
    fun twoQuickTwistsDrive() {
        assertEquals(1, recognised(twists(twists = 2, periodMs = 500, peak = 6f)))
    }

    @Test
    fun oneTwistDoesNotDrive() {
        assertEquals(0, recognised(twists(twists = 1, periodMs = 500, peak = 6f)))
    }

    @Test
    fun twistsSpreadPastTheWindowDoNotDrive() {
        assertEquals(0, recognised(twists(twists = 2, periodMs = 1_200, peak = 6f)))
    }

    @Test
    fun rotationBelowTheThresholdDoesNotDrive() {
        assertEquals(0, recognised(twists(twists = 10, periodMs = 500, peak = 3f)))
    }

    @Test
    fun turningOneWayOnlyDoesNotDrive() {
        val oneWay = twists(twists = 6, periodMs = 500, peak = 6f).map { max(it, 0f) }
        assertEquals(0, recognised(oneWay))
    }

    @Test
    fun oneGestureDrivesOneStepOnly() {
        assertEquals(1, recognised(twists(twists = 3, periodMs = 500, peak = 6f)))
    }

    @Test
    fun aSecondGestureDrivesAgain() {
        val gesture = twists(twists = 2, periodMs = 500, peak = 6f)
        assertEquals(2, recognised(gesture + still(1_200) + gesture))
    }
}
