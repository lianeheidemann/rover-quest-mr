package io.github.lianeheidemann.roverquest.core.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How far the rover wiggles left and right while dancing, in degrees. */
const val DANCE_WIGGLE_DEGREES = 25f

/** How high the rover hops while dancing, in meters. */
const val DANCE_HOP_HEIGHT = 0.04f

private const val FACE_SECONDS = 0.6f
private const val WIGGLE_SECONDS = 1.0f
private const val SPIN_SECONDS = 1.0f
private const val HOPS_SECONDS = 0.8f

/** 0 → 1 with a slow start and end. */
private fun ease(f: Float) = (1 - cos(PI * f).toFloat()) / 2

/**
 * Victory dance when the rover reaches the destination: it turns to face the player, wiggles
 * twice, spins all the way round and hops twice, always facing the player while hopping. Pure
 * animation (the rover's heading and a lift off the floor as functions of time); it changes
 * nothing in the game.
 *
 * @param from the rover's heading when it arrived, in degrees.
 * @param facing heading that points the rover's face at the player, in degrees.
 * @param elapsed seconds of the dance already played.
 */
data class RoverDance(val from: Float = 0f, val facing: Float = 0f, val elapsed: Float = 0f) {

    val duration: Float get() = FACE_SECONDS + WIGGLE_SECONDS + SPIN_SECONDS + HOPS_SECONDS

    val done: Boolean get() = elapsed >= duration

    /** Advances the dance by [seconds]; it waits while [tracking] is lost. */
    fun step(seconds: Float, tracking: Boolean): RoverDance =
        if (!tracking || done) this else copy(elapsed = min(elapsed + seconds, duration))

    /** Where the rover points now, in degrees; it ends facing the player. */
    val heading: Float
        get() {
            var t = elapsed
            if (done) return normalizeDegrees(facing)
            if (t < FACE_SECONDS) {
                return normalizeDegrees(from + angleDifference(facing, from) * ease(t / FACE_SECONDS))
            }
            t -= FACE_SECONDS
            if (t < WIGGLE_SECONDS) {
                return normalizeDegrees(facing + DANCE_WIGGLE_DEGREES * sin(4 * PI * t / WIGGLE_SECONDS).toFloat())
            }
            t -= WIGGLE_SECONDS
            if (t < SPIN_SECONDS) return normalizeDegrees(facing + 360f * ease(t / SPIN_SECONDS))
            return normalizeDegrees(facing)
        }

    /** Height off the floor now, in meters: two hops at the end, facing the player. */
    val lift: Float
        get() {
            val t = elapsed - FACE_SECONDS - WIGGLE_SECONDS - SPIN_SECONDS
            if (done || t <= 0f || t >= HOPS_SECONDS) return 0f
            return DANCE_HOP_HEIGHT * abs(sin(2 * PI * t / HOPS_SECONDS).toFloat())
        }
}
