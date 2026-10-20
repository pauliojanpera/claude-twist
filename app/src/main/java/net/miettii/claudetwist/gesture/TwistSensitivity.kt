package net.miettii.claudetwist.gesture

// How insistent the drive gesture has to be. A pocket on a walk makes motions of its own, so the
// briefer the gesture demanded, the more of those are mistaken for one — and the longer it is
// demanded, the more often a real one has to be repeated. Only a walk settles the trade, so the
// holder picks.
//
// The two numbers move together and cannot be set apart: a window mitred for four swings would let a
// single lazy roll out and back a second later count as one twist, which is exactly the motion a
// thigh makes. Fewer swings therefore come with a tighter window, which is what forces the gesture
// to be brisk rather than merely large.
enum class TwistSensitivity(val swings: Int, windowMillis: Long) {

    /** One brisk twist, out and back. The quickest to perform, and the easiest for a pocket to fake. */
    OneTwist(swings = 2, windowMillis = 700),

    /** A twist and a half: the return stroke carried on past where a pocket's own roll would stop. */
    OneAndAHalfTwists(swings = 3, windowMillis = 1_100),

    /** Two twists, out and back twice — the least mistakable, and the slowest to perform. */
    TwoTwists(swings = 4, windowMillis = 1_500),
    ;

    val windowNanos: Long = windowMillis * 1_000_000L
}
