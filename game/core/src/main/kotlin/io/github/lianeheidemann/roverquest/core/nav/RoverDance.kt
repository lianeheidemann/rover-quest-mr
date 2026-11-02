package io.github.lianeheidemann.roverquest.core.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** How far the rover wiggles left and right while dancing, in degrees. */
const val DANCE_WIGGLE_DEGREES = 25f

/** How high the rover hops while dancing, in meters. */
const val DANCE_HOP_HEIGHT = 0.04f

private const val WIGGLE_SECONDS = 1.0f
private const val SPIN_SECONDS = 1.0f
private const val HOPS_SECONDS = 0.8f

/**
 * Victory dance when the rover reaches the destination: two quick wiggles, a full spin and two
 * little hops. Pure animation on top of the rover's pose: a turn added to its heading and a lift
 * off the floor, as functions of time. It changes nothing in the game.
 *
 * @param elapsed seconds of the dance already played.
 */
data class RoverDance(val elapsed: Float = 0f) {

    val duration: Float get() = WIGGLE_SECONDS + SPIN_SECONDS + HOPS_SECONDS

    val done: Boolean get() = elapsed >= duration

    /** Advances the dance by [seconds]; it waits while [tracking] is lost. */
    fun step(seconds: Float, tracking: Boolean): RoverDance =
        if (!tracking || done) this else copy(elapsed = min(elapsed + seconds, duration))

    /** Degrees added to the rover's heading now; back to `0` at the end. */
    val turn: Float
        get() {
            val t = elapsed
            return when {
                done -> 0f
                t < WIGGLE_SECONDS -> DANCE_WIGGLE_DEGREES * sin(4 * PI * t / WIGGLE_SECONDS).toFloat()
                t < WIGGLE_SECONDS + SPIN_SECONDS -> {
                    // Eased full turn: slow start and end, quick in the middle.
                    val f = (t - WIGGLE_SECONDS) / SPIN_SECONDS
                    val eased = (1 - kotlin.math.cos(PI * f).toFloat()) / 2
                    normalizeDegrees(360f * eased)
                }
                else -> 0f
            }
        }

    /** Height off the floor now, in meters: two hops at the end. */
    val lift: Float
        get() {
            val t = elapsed - WIGGLE_SECONDS - SPIN_SECONDS
            if (done || t <= 0f || t >= HOPS_SECONDS) return 0f
            return DANCE_HOP_HEIGHT * abs(sin(2 * PI * t / HOPS_SECONDS).toFloat())
        }
}
