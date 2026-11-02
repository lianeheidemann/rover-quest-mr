package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.headingDegrees
import kotlin.math.abs
import kotlin.math.min

/** Driving speed, in meters per second. */
const val ROVER_SPEED = 0.15f

/** Turning speed, in degrees per second. */
const val ROVER_TURN_SPEED = 120f

/** Above this heading error the rover stops and turns in place before driving on. */
const val TURN_IN_PLACE_ABOVE = 20f

/** Closer than this, in meters, a waypoint counts as reached. */
const val WAYPOINT_REACHED = 0.02f

/**
 * The rover stops this far, in meters, from the destination's center, wherever on the route it
 * first gets that close: just short of the flag, never on top of it. Smaller than the closest a
 * destination can be placed.
 */
const val ARRIVAL_DISTANCE = 0.22f

/** Where the rover is on the floor (anchor frame) and where its front (-Z) points, in degrees. */
data class RoverPose(val position: FloorPoint = FloorPoint.ORIGIN, val heading: Float = 0f)

enum class MotionStatus {
    /** Waiting for the player to start. */
    IDLE,

    /** Turning in place towards the next waypoint. */
    TURNING,

    /** Driving towards the next waypoint. */
    DRIVING,

    /** Started, but it is not safe to move right now (see [PauseReason]). */
    PAUSED,

    /** Reached the destination. */
    ARRIVED,
}

enum class PauseReason {
    /** The camera or the floor anchor lost tracking: positions are not trustworthy. */
    TRACKING_LOST,

    /** No route through floor seen as free (part of the way unseen, or blocked). */
    NO_ROUTE,
}

/**
 * The rover following a route. Pure state: [step] advances it by a time slice.
 *
 * @param route the route being followed; its first point is where it was planned from.
 * @param target index in [route] of the waypoint the rover is heading to.
 */
data class RoverMotion(
    val pose: RoverPose = RoverPose(),
    val status: MotionStatus = MotionStatus.IDLE,
    val pauseReason: PauseReason? = null,
    val route: List<FloorPoint> = emptyList(),
    val target: Int = 1,
) {
    val isRunning: Boolean
        get() = status == MotionStatus.TURNING || status == MotionStatus.DRIVING || status == MotionStatus.PAUSED

    /** Starts (or restarts) driving along [route], planned from the current pose. */
    fun start(route: List<FloorPoint>): RoverMotion =
        copy(status = MotionStatus.TURNING, pauseReason = null, route = route, target = 1)

    /** Stops where it is and waits for the player. */
    fun stop(): RoverMotion = copy(status = MotionStatus.IDLE, pauseReason = null)

    /** Follows a replanned route (planned from the current pose) without stopping. */
    fun withRoute(route: List<FloorPoint>): RoverMotion =
        if (route == this.route) this else copy(route = route, target = 1)

    /** Distance already covered along [route], in meters, for hiding the dots left behind. */
    val progress: Float
        get() {
            if (route.size < 2) return 0f
            val t = target.coerceIn(1, route.size - 1)
            var done = 0f
            for (i in 1 until t) done += route[i - 1].distanceTo(route[i])
            val segment = route[t - 1].distanceTo(route[t])
            return done + (segment - pose.position.distanceTo(route[t])).coerceIn(0f, segment)
        }

    /**
     * Advances the rover by [seconds].
     *
     * @param tracking camera and floor anchor are tracking; otherwise the rover pauses.
     * @param routeAvailable a route through known free floor exists; otherwise the rover pauses.
     */
    fun step(seconds: Float, tracking: Boolean, routeAvailable: Boolean): RoverMotion {
        if (!isRunning) return this
        if (!tracking) return copy(status = MotionStatus.PAUSED, pauseReason = PauseReason.TRACKING_LOST)
        if (!routeAvailable || route.size < 2) return copy(status = MotionStatus.PAUSED, pauseReason = PauseReason.NO_ROUTE)

        var position = pose.position
        var heading = pose.heading
        var index = target
        var time = seconds
        var status = MotionStatus.DRIVING

        while (time > 1e-4f) {
            // Waypoints within reach are taken exactly, so corners are not cut.
            while (index < route.size - 1 && position.distanceTo(route[index]) < WAYPOINT_REACHED) {
                position = route[index]
                index++
            }
            // The destination is reached just short of its flag, on whatever leg the rover is.
            if (index >= route.size || position.distanceTo(route.last()) <= ARRIVAL_DISTANCE + 1e-4f) {
                return copy(
                    pose = RoverPose(position, heading),
                    status = MotionStatus.ARRIVED,
                    pauseReason = null,
                    target = route.size - 1,
                )
            }
            val waypoint = route[index]
            val error = angleDifference(headingDegrees(position, waypoint), heading)

            // Turn first when far off course; otherwise steer while driving.
            val maxTurn = ROVER_TURN_SPEED * time
            if (abs(error) > TURN_IN_PLACE_ABOVE) {
                val turn = error.coerceIn(-maxTurn, maxTurn)
                heading = normalizeDegrees(heading + turn)
                time -= abs(turn) / ROVER_TURN_SPEED
                status = MotionStatus.TURNING
                continue
            }
            heading = normalizeDegrees(heading + error.coerceIn(-maxTurn, maxTurn))

            val distance = position.distanceTo(waypoint)
            // Stop where this leg first comes within ARRIVAL_DISTANCE of the destination.
            val stopAt = firstWithin(position, waypoint, route.last(), ARRIVAL_DISTANCE) ?: distance
            val travel = min(ROVER_SPEED * time, stopAt)
            val t = travel / distance
            position = FloorPoint(position.x + (waypoint.x - position.x) * t, position.z + (waypoint.z - position.z) * t)
            time -= travel / ROVER_SPEED
            status = MotionStatus.DRIVING
        }
        return copy(pose = RoverPose(position, heading), status = status, pauseReason = null, target = index)
    }
}

/**
 * Distance along the segment [from] → [to] to the first point within [radius] of [center], or
 * `null` when the segment never gets that close.
 */
internal fun firstWithin(from: FloorPoint, to: FloorPoint, center: FloorPoint, radius: Float): Float? {
    val length = from.distanceTo(to)
    if (length == 0f) return null
    val dx = (to.x - from.x) / length
    val dz = (to.z - from.z) / length
    val fx = from.x - center.x
    val fz = from.z - center.z
    // |f + s·d|² = r²  →  s² + 2(f·d)s + |f|² − r² = 0
    val b = fx * dx + fz * dz
    val c = fx * fx + fz * fz - radius * radius
    val disc = b * b - c
    if (disc < 0f) return null
    val s = -b - kotlin.math.sqrt(disc)
    return if (s in 0f..length) s else null
}

/** Shortest signed turn from [from] to [to], in degrees, within (-180, 180]. */
fun angleDifference(to: Float, from: Float): Float = normalizeDegrees(to - from)

/** The same angle within (-180, 180]. */
fun normalizeDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}
