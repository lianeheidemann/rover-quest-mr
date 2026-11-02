package io.github.lianeheidemann.roverquest.ui.ar

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.PlacementRejection
import io.github.lianeheidemann.roverquest.core.floor.PlacementResult
import io.github.lianeheidemann.roverquest.core.floor.SetupPhase
import io.github.lianeheidemann.roverquest.core.floor.headingDegrees
import io.github.lianeheidemann.roverquest.core.floor.roverHeading
import io.github.lianeheidemann.roverquest.core.nav.MotionStatus
import io.github.lianeheidemann.roverquest.core.nav.ObstacleMap
import io.github.lianeheidemann.roverquest.core.nav.destinationRejection
import io.github.lianeheidemann.roverquest.core.nav.RoutePlan
import io.github.lianeheidemann.roverquest.core.nav.RoverDance
import io.github.lianeheidemann.roverquest.core.nav.RoverGreeting
import io.github.lianeheidemann.roverquest.core.nav.RoverMotion
import io.github.lianeheidemann.roverquest.core.nav.RoverPose
import io.github.sceneview.ar.arcore.createAnchorOrNull

internal const val TAG = "RoverQuest"

/** What a tap does once the rover is on the floor. */
internal enum class TapMode { DESTINATION, OBSTACLE }

/**
 * Everything one game keeps between frames: the setup on the floor, the rover and its animations.
 * Only holds state and forwards actions to `:core`, where the rules live.
 */
@Stable
internal class GameState {
    var setup by mutableStateOf(FloorSetup())
    var rejection by mutableStateOf<PlacementRejection?>(null)
    var tapMode by mutableStateOf(TapMode.DESTINATION)

    /** Floor anchor, created where the rover is placed; every game position is relative to it. */
    var floorAnchor by mutableStateOf<Anchor?>(null)
        private set
    var anchorState by mutableStateOf(TrackingState.TRACKING)

    /** Rover driving along the route. */
    var motion by mutableStateOf(RoverMotion())
        private set

    /** Where routes are planned from: the rover's pose when idle, its pose at the last replan while driving. */
    var routeStart by mutableStateOf(FloorPoint.ORIGIN)
        private set

    /** When first placed, the rover drives up to the player to say hello, then goes back. */
    var greeting by mutableStateOf<RoverGreeting?>(null)
        private set
    private var greeted = false

    /** Victory dance when the rover reaches the destination. */
    var dance by mutableStateOf<RoverDance?>(null)
        private set

    /** Bumped when the rover is removed, to load fresh 3D models for the next one. */
    var modelGeneration by mutableIntStateOf(0)
        private set

    /** Latest camera position in world space, written every frame. */
    val cameraPosition = FloatArray(3)

    /** Where the rover is drawn: animations first, else it faces where it will go while idle. */
    fun roverPose(route: RoutePlan?): RoverPose {
        greeting?.let { return it.pose }
        dance?.let { return motion.pose.copy(heading = it.heading) }
        if (motion.status != MotionStatus.IDLE) return motion.pose
        val heading = roverHeading(setup, (route as? RoutePlan.Route)?.points, from = motion.pose.position)
        return motion.pose.copy(heading = heading)
    }

    /** The point on the floor under the phone, in the anchor's frame. */
    private fun playerPoint(anchor: Anchor): FloorPoint {
        val player = anchor.pose.inverse().transformPoint(cameraPosition)
        return FloorPoint(player[0], player[2])
    }

    fun confirmArea() {
        setup = setup.confirmArea()
    }

    fun selectTapMode(mode: TapMode) {
        tapMode = mode
        rejection = null
    }

    fun removeObstacle() {
        setup = setup.withoutObstacle()
    }

    /**
     * A tap on the floor at [hit]: places the rover first, then the destination or the obstacle.
     *
     * @param map what depth has seen, or `null` without depth: then the destination is not
     *   checked against the map.
     */
    fun onTap(hit: HitResult?, cameraTracking: Boolean, map: ObstacleMap?) {
        // Nothing is placed until the player accepts the detected floor area.
        if (setup.phase == SetupPhase.CONFIRM_AREA) return
        // Taps wait until the rover is back from greeting the player.
        if (greeting != null) return
        if (hit == null || !cameraTracking) return
        rejection = null
        val anchor = floorAnchor
        if (anchor == null) {
            val created = hit.createAnchorOrNull() ?: return
            floorAnchor = created
            anchorState = TrackingState.TRACKING
            setup = setup.withRover()
            Log.i(TAG, "Rover placed")
            if (!greeted) {
                greeted = true
                greeting = RoverGreeting.towards(playerPoint(created))
            }
            return
        }
        val local = anchor.pose.inverse().transformPoint(hit.hitPose.translation)
        val result = when (tapMode) {
            TapMode.DESTINATION -> map?.let { destinationRejection(it, FloorPoint(local[0], local[2])) }
                ?.let { PlacementResult.Rejected(it) }
                // The destination must also be on floor depth has seen, and not on a seen obstacle.
                ?: setup.placeDestination(local[0], local[1], local[2], rover = motion.pose.position)
            TapMode.OBSTACLE -> setup.placeObstacle(local[0], local[1], local[2], rover = motion.pose.position)
        }
        when (result) {
            is PlacementResult.Accepted -> {
                setup = result.setup
                // Destination set: the next tap most likely places the obstacle.
                if (tapMode == TapMode.DESTINATION) tapMode = TapMode.OBSTACLE
                // A new destination after arriving: wait for the player to start again.
                if (motion.status == MotionStatus.ARRIVED) {
                    motion = motion.stop()
                    dance = null
                }
                Log.i(TAG, "Placed $tapMode: destination=${setup.destination} obstacle=${setup.obstacle}")
            }
            is PlacementResult.Rejected -> rejection = result.reason
        }
    }

    fun go(route: RoutePlan?) {
        val points = (route as? RoutePlan.Route)?.points ?: return
        motion = motion.copy(pose = roverPose(route)).start(points)
        Log.i(TAG, "Rover started: ${points.size} waypoints")
    }

    fun stop() {
        motion = motion.stop()
    }

    /** Plans from the rover: from its pose while it waits (called when the motion status changes). */
    fun onMotionStatusChanged() {
        if (!motion.isRunning) routeStart = motion.pose.position
        if (motion.status == MotionStatus.ARRIVED) {
            // Dance facing the player: towards the point on the floor under the phone.
            val facing = floorAnchor?.let { headingDegrees(motion.pose.position, playerPoint(it)) } ?: motion.pose.heading
            dance = RoverDance(from = motion.pose.heading, facing = facing)
        }
    }

    /** Replans from where the rover is when the setup or the map changes while it drives. */
    fun onPlanInputsChanged() {
        if (motion.isRunning) routeStart = motion.pose.position
    }

    /** Follows a replanned route without stopping. */
    fun onRouteChanged(route: RoutePlan?) {
        val points = (route as? RoutePlan.Route)?.points
        if (motion.isRunning && points != null) motion = motion.withRoute(points)
    }

    /** One frame of driving; returns whether the rover still runs. */
    fun stepMotion(seconds: Float, tracking: Boolean, routeAvailable: Boolean): Boolean {
        motion = motion.step(seconds, tracking, routeAvailable)
        return motion.isRunning
    }

    /** One frame of the greeting; returns whether it still plays. */
    fun stepGreeting(seconds: Float, tracking: Boolean): Boolean {
        val next = greeting?.step(seconds, tracking)
        greeting = next?.takeUnless { it.done }
        return greeting != null
    }

    /** One frame of the dance; returns whether it still plays. */
    fun stepDance(seconds: Float, tracking: Boolean): Boolean {
        val next = dance?.step(seconds, tracking) ?: return false
        if (!next.done) {
            dance = next
            return true
        }
        // Keep facing the player after the dance instead of snapping back.
        motion = motion.copy(pose = motion.pose.copy(heading = next.heading))
        dance = null
        return false
    }

    /** "Recomeçar": removes the rover and everything placed, keeping the accepted floor area. */
    fun restart() {
        // Its models go with the rover's nodes: load fresh ones for the next rover.
        if (floorAnchor != null) modelGeneration++
        floorAnchor?.detach()
        floorAnchor = null
        setup = setup.reset()
        rejection = null
        tapMode = TapMode.DESTINATION
        motion = RoverMotion()
        greeting = null
        dance = null
        routeStart = FloorPoint.ORIGIN
    }

    /** A new AR session: new floor planes, so the area must be accepted again and the rover greets again. */
    fun newGame() {
        restart()
        setup = FloorSetup()
        greeted = false
    }
}

@Composable
internal fun rememberGameState(): GameState = remember { GameState() }
