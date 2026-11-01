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

enum class SetupPhase { PLACE_ROVER, PLACE_DESTINATION, READY }

enum class DestinationRejection { NO_ROVER, NOT_SAME_FLOOR, TOO_CLOSE }

sealed interface DestinationResult {
    data class Accepted(val setup: FloorSetup) : DestinationResult
    data class Rejected(val reason: DestinationRejection) : DestinationResult
}

/**
 * Rover and destination placed on the floor. The rover defines the floor anchor, so it is
 * always at [FloorPoint.ORIGIN]; the destination is stored relative to it.
 */
data class FloorSetup(
    val roverPlaced: Boolean = false,
    val destination: FloorPoint? = null,
) {
    val phase: SetupPhase
        get() = when {
            !roverPlaced -> SetupPhase.PLACE_ROVER
            destination == null -> SetupPhase.PLACE_DESTINATION
            else -> SetupPhase.READY
        }

    fun withRover(): FloorSetup = FloorSetup(roverPlaced = true)

    fun reset(): FloorSetup = FloorSetup()

    /**
     * Places (or moves) the destination at the given offset from the floor anchor, in meters,
     * with `dy` pointing up from the floor.
     */
    fun placeDestination(dx: Float, dy: Float, dz: Float): DestinationResult {
        if (!roverPlaced) return DestinationResult.Rejected(DestinationRejection.NO_ROVER)
        if (abs(dy) > MAX_FLOOR_HEIGHT_DIFFERENCE) {
            return DestinationResult.Rejected(DestinationRejection.NOT_SAME_FLOOR)
        }
        val point = FloorPoint(dx, dz)
        if (point.distanceTo(FloorPoint.ORIGIN) < MIN_DESTINATION_DISTANCE) {
            return DestinationResult.Rejected(DestinationRejection.TOO_CLOSE)
        }
        return DestinationResult.Accepted(copy(destination = point))
    }

    companion object {
        /** Taps higher or lower than this hit another surface (table, sofa), not the rover's floor. */
        const val MAX_FLOOR_HEIGHT_DIFFERENCE = 0.10f

        /** The destination must be clearly apart from the rover. */
        const val MIN_DESTINATION_DISTANCE = 0.20f
    }
}

/** What the player should do next, in priority order. */
enum class SetupHint {
    TRACKING_LOST,
    ANCHOR_LOST,
    SCAN_FLOOR,
    AIM_AT_FLOOR,
    TAP_TO_PLACE_ROVER,
    TAP_TO_PLACE_DESTINATION,
    READY,
}

/**
 * Decides the hint shown to the player. Problems that block everything (tracking, lost anchor)
 * win over placement instructions.
 *
 * @param anchorStopped the floor anchor will never track again; the player must start over.
 * @param floorDetected at least one floor surface has been found.
 * @param aimingAtFloor the placement reticle currently hits a floor surface.
 */
fun setupHint(
    setup: FloorSetup,
    cameraTracking: Boolean,
    anchorStopped: Boolean,
    floorDetected: Boolean,
    aimingAtFloor: Boolean,
): SetupHint = when {
    setup.roverPlaced && anchorStopped -> SetupHint.ANCHOR_LOST
    !cameraTracking -> SetupHint.TRACKING_LOST
    setup.phase == SetupPhase.READY -> SetupHint.READY
    !floorDetected -> SetupHint.SCAN_FLOOR
    !aimingAtFloor -> SetupHint.AIM_AT_FLOOR
    setup.phase == SetupPhase.PLACE_ROVER -> SetupHint.TAP_TO_PLACE_ROVER
    else -> SetupHint.TAP_TO_PLACE_DESTINATION
}
