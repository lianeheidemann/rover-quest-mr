package io.github.lianeheidemann.roverquest.core.floor

import kotlin.math.abs
import kotlin.math.hypot

/** A point on the floor, in meters, relative to the floor anchor (the rover sits at the origin). */
data class FloorPoint(val x: Float, val z: Float) {
    fun distanceTo(other: FloorPoint): Float = hypot(x - other.x, z - other.z)

    companion object {
        val ORIGIN = FloorPoint(0f, 0f)
    }
}

/** Game setup steps, in order. The player first accepts the detected floor area. */
enum class SetupPhase { CONFIRM_AREA, PLACE_ROVER, PLACE_DESTINATION, READY }

enum class PlacementRejection {
    NO_ROVER,
    NOT_SAME_FLOOR,
    OUT_OF_AREA,
    TOO_CLOSE,
    BLOCKED_BY_OBSTACLE,
    COVERS_ROVER,
    COVERS_DESTINATION,
}

sealed interface PlacementResult {
    data class Accepted(val setup: FloorSetup) : PlacementResult
    data class Rejected(val reason: PlacementRejection) : PlacementResult
}

/**
 * Rover, destination and manual obstacle placed on the floor. The rover defines the floor anchor,
 * so it is always at [FloorPoint.ORIGIN]; everything else is stored relative to it.
 *
 * @param areaConfirmed the player accepted the detected floor area; nothing can be placed before.
 */
data class FloorSetup(
    val areaConfirmed: Boolean = false,
    val roverPlaced: Boolean = false,
    val destination: FloorPoint? = null,
    val obstacle: FloorPoint? = null,
) {
    val phase: SetupPhase
        get() = when {
            !areaConfirmed -> SetupPhase.CONFIRM_AREA
            !roverPlaced -> SetupPhase.PLACE_ROVER
            destination == null -> SetupPhase.PLACE_DESTINATION
            else -> SetupPhase.READY
        }

    fun confirmArea(): FloorSetup = copy(areaConfirmed = true)

    /** Places the rover; ignored until the area is confirmed. */
    fun withRover(): FloorSetup = if (areaConfirmed) FloorSetup(areaConfirmed = true, roverPlaced = true) else this

    /** Clears rover, destination and obstacle, keeping the confirmed area ("Recomeçar"). */
    fun reset(): FloorSetup = FloorSetup(areaConfirmed = areaConfirmed)

    /**
     * Places (or moves) the destination at the given offset from the floor anchor, in meters,
     * with `dy` pointing up from the floor.
     */
    fun placeDestination(dx: Float, dy: Float, dz: Float): PlacementResult {
        floorPointOrRejection(dx, dy, dz)?.let { return PlacementResult.Rejected(it) }
        val point = FloorPoint(dx, dz)
        return when {
            point.distanceTo(FloorPoint.ORIGIN) < MIN_DESTINATION_DISTANCE ->
                PlacementResult.Rejected(PlacementRejection.TOO_CLOSE)
            obstacle?.let { obstacleBlocks(it, point) } == true ->
                PlacementResult.Rejected(PlacementRejection.BLOCKED_BY_OBSTACLE)
            else -> PlacementResult.Accepted(copy(destination = point))
        }
    }

    /** Places (or moves) the manual test obstacle; same coordinates as [placeDestination]. */
    fun placeObstacle(dx: Float, dy: Float, dz: Float): PlacementResult {
        floorPointOrRejection(dx, dy, dz)?.let { return PlacementResult.Rejected(it) }
        val point = FloorPoint(dx, dz)
        return when {
            obstacleBlocks(point, FloorPoint.ORIGIN) ->
                PlacementResult.Rejected(PlacementRejection.COVERS_ROVER)
            destination?.let { obstacleBlocks(point, it) } == true ->
                PlacementResult.Rejected(PlacementRejection.COVERS_DESTINATION)
            else -> PlacementResult.Accepted(copy(obstacle = point))
        }
    }

    fun withoutObstacle(): FloorSetup = copy(obstacle = null)

    private fun floorPointOrRejection(dx: Float, dy: Float, dz: Float): PlacementRejection? = when {
        !roverPlaced -> PlacementRejection.NO_ROVER
        abs(dy) > MAX_FLOOR_HEIGHT_DIFFERENCE -> PlacementRejection.NOT_SAME_FLOOR
        abs(dx) > MAX_REACH || abs(dz) > MAX_REACH -> PlacementRejection.OUT_OF_AREA
        else -> null
    }

    companion object {
        /** Taps higher or lower than this hit another surface (table, sofa), not the rover's floor. */
        const val MAX_FLOOR_HEIGHT_DIFFERENCE = 0.10f

        /** The destination must be clearly apart from the rover. */
        const val MIN_DESTINATION_DISTANCE = 0.20f

        /** Half the side of the square play area centered on the rover. */
        const val PLAY_AREA_HALF_SIZE = 2.5f

        /** Rover footprint radius, used as clearance around obstacles. */
        const val ROVER_RADIUS = 0.10f

        /** Side of the square manual obstacle. */
        const val OBSTACLE_SIZE = 0.30f

        /** Things must be placed far enough from the edge for the rover to reach them. */
        const val MAX_REACH = PLAY_AREA_HALF_SIZE - ROVER_RADIUS

        /** Half side of the area the rover's center cannot enter around an obstacle. */
        const val OBSTACLE_BLOCKED_HALF_SIZE = OBSTACLE_SIZE / 2 + ROVER_RADIUS

        /** True when the rover's center at [point] would touch the obstacle at [obstacle]. */
        fun obstacleBlocks(obstacle: FloorPoint, point: FloorPoint): Boolean =
            abs(point.x - obstacle.x) <= OBSTACLE_BLOCKED_HALF_SIZE &&
                abs(point.z - obstacle.z) <= OBSTACLE_BLOCKED_HALF_SIZE
    }
}

/** What the player should do next, in priority order. */
enum class SetupHint {
    TRACKING_LOST,
    ANCHOR_LOST,
    SCAN_FLOOR,
    CONFIRM_AREA,
    AIM_AT_FLOOR,
    TAP_TO_PLACE_ROVER,
    TAP_TO_PLACE_DESTINATION,
    NEEDS_SCAN,
    NO_ROUTE,
    READY,
}

/**
 * Decides the hint shown to the player. Problems that block everything (tracking, lost anchor)
 * win over placement instructions.
 *
 * @param anchorStopped the floor anchor will never track again; the player must start over.
 * @param floorDetected at least one floor surface has been found.
 * @param aimingAtFloor the placement reticle currently hits a floor surface.
 * @param routeBlocked rover and destination are placed but no route connects them.
 * @param routeNeedsScan a route may exist, but part of the way was never observed.
 */
fun setupHint(
    setup: FloorSetup,
    cameraTracking: Boolean,
    anchorStopped: Boolean,
    floorDetected: Boolean,
    aimingAtFloor: Boolean,
    routeBlocked: Boolean = false,
    routeNeedsScan: Boolean = false,
): SetupHint = when {
    setup.roverPlaced && anchorStopped -> SetupHint.ANCHOR_LOST
    !cameraTracking -> SetupHint.TRACKING_LOST
    setup.phase == SetupPhase.READY -> when {
        routeBlocked -> SetupHint.NO_ROUTE
        routeNeedsScan -> SetupHint.NEEDS_SCAN
        else -> SetupHint.READY
    }
    !floorDetected -> SetupHint.SCAN_FLOOR
    setup.phase == SetupPhase.CONFIRM_AREA -> SetupHint.CONFIRM_AREA
    !aimingAtFloor -> SetupHint.AIM_AT_FLOOR
    setup.phase == SetupPhase.PLACE_ROVER -> SetupHint.TAP_TO_PLACE_ROVER
    else -> SetupHint.TAP_TO_PLACE_DESTINATION
}
