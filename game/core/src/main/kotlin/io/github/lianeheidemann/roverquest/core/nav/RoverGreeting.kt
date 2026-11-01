package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.headingDegrees
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Speed of the greeting drive, in meters per second: livelier than the route drive. */
const val GREETING_SPEED = 0.3f

/** The greeting stops this far, in meters, from the point on the floor under the phone. */
const val GREETING_STOP_SHORT = 0.4f

/** The greeting never drives farther than this from where the rover was placed, in meters. */
const val GREETING_MAX_DISTANCE = 1.2f

/** Shorter approaches are skipped: the rover only spins in place. */
const val GREETING_MIN_DISTANCE = 0.15f

/** How long the rover waves at the player, in seconds. */
const val GREETING_WAVE_SECONDS = 1.2f

/** How far the rover swings left and right while waving, in degrees. */
const val GREETING_WAVE_DEGREES = 20f

/**
 * Short show when the rover is first placed: it spins round to face the player, drives up close,
 * waves, spins round again, drives back to where it was placed and faces ahead (-Z, heading 0).
 * Pure animation, a function of time: it changes nothing in the game.
 *
 * @param approach where the rover stops near the player, in the floor anchor's frame.
 * @param elapsed seconds of the show already played.
 */
data class RoverGreeting(val approach: FloorPoint, val elapsed: Float = 0f) {

    private val legs: List<Leg> = buildLegs(approach)

    /** Total length of the show, in seconds. */
    val duration: Float = legs.sumOf { it.seconds.toDouble() }.toFloat()

    val done: Boolean
        get() = elapsed >= duration

    /** Advances the show by [seconds]; it waits while [tracking] is lost, like the route drive. */
    fun step(seconds: Float, tracking: Boolean): RoverGreeting =
        if (!tracking || done) this else copy(elapsed = min(elapsed + seconds, duration))

    /** Skips to the end. */
    fun finish(): RoverGreeting = copy(elapsed = duration)

    /** Where the rover is now. */
    val pose: RoverPose
        get() {
            // Exactly back home, facing ahead, without float leftovers from the legs.
            if (done) return RoverPose(FloorPoint.ORIGIN, 0f)
            var t = elapsed
            for (leg in legs) {
                if (t <= leg.seconds) return leg.pose(t)
                t -= leg.seconds
            }
            return legs.last().pose(legs.last().seconds)
        }

    companion object {
        /**
         * The greeting towards [player], the point on the floor under the phone (anchor frame):
         * the rover stops [GREETING_STOP_SHORT] before it, at most [GREETING_MAX_DISTANCE] away.
         */
        fun towards(player: FloorPoint): RoverGreeting {
            val distance = player.distanceTo(FloorPoint.ORIGIN)
            val travel = (distance - GREETING_STOP_SHORT).coerceAtMost(GREETING_MAX_DISTANCE)
            if (travel < GREETING_MIN_DISTANCE) return RoverGreeting(FloorPoint.ORIGIN)
            val scale = travel / distance
            return RoverGreeting(FloorPoint(player.x * scale, player.z * scale))
        }
    }
}

/** One piece of the show, with the pose [t] seconds after it starts. */
private interface Leg {
    val seconds: Float
    fun pose(t: Float): RoverPose
}

/** Turns in place at [at] from [from] by [by] degrees (positive is counter-clockwise from above). */
private class Turn(val at: FloorPoint, val from: Float, val by: Float) : Leg {
    override val seconds = kotlin.math.abs(by) / ROVER_TURN_SPEED
    override fun pose(t: Float) =
        RoverPose(at, normalizeDegrees(from + by * if (seconds == 0f) 1f else (t / seconds)))
}

/** Drives straight from [from] to [to], facing [heading]. */
private class Drive(val from: FloorPoint, val to: FloorPoint, val heading: Float) : Leg {
    override val seconds = from.distanceTo(to) / GREETING_SPEED
    override fun pose(t: Float): RoverPose {
        if (t >= seconds) return RoverPose(to, heading)
        val f = t / seconds
        return RoverPose(FloorPoint(from.x + (to.x - from.x) * f, from.z + (to.z - from.z) * f), heading)
    }
}

/** Swings left and right twice around [heading], like a wave. */
private class Wave(val at: FloorPoint, val heading: Float) : Leg {
    override val seconds = GREETING_WAVE_SECONDS
    override fun pose(t: Float) = RoverPose(
        at,
        normalizeDegrees(heading + GREETING_WAVE_DEGREES * sin(4 * PI * t / seconds).toFloat()),
    )
}

private fun buildLegs(approach: FloorPoint): List<Leg> {
    val home = FloorPoint.ORIGIN
    // Placed facing ahead (-Z); the player usually stands behind it, so this is about half a turn.
    // Without room to drive it still spins a half turn to face back.
    val facePlayer = if (approach == home) 180f else headingDegrees(home, approach)
    val firstTurn = angleDifference(facePlayer, 0f).let { if (it == -180f) 180f else it }
    val faceHome = normalizeDegrees(facePlayer + 180f)
    return listOf(
        Turn(home, 0f, firstTurn),
        Drive(home, approach, facePlayer),
        Wave(approach, facePlayer),
        Turn(approach, facePlayer, 180f),
        Drive(approach, home, faceHome),
        Turn(home, faceHome, angleDifference(0f, faceHome)),
    )
}
