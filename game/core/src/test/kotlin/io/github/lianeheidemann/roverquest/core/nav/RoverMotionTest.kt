package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.PlacementResult
import io.github.lianeheidemann.roverquest.core.floor.SetupHint
import io.github.lianeheidemann.roverquest.core.floor.headingDegrees
import io.github.lianeheidemann.roverquest.core.floor.setupHint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoverMotionTest {

    private val straightAhead = listOf(FloorPoint.ORIGIN, FloorPoint(0f, -0.6f)) // front is -Z

    /** Runs the rover at 60 frames per second for [seconds]. */
    private fun RoverMotion.run(seconds: Float, tracking: Boolean = true, routeAvailable: Boolean = true): RoverMotion {
        var m = this
        repeat((seconds * 60).toInt()) { m = m.step(1f / 60, tracking, routeAvailable) }
        return m
    }

    @Test
    fun `idle rover does not move`() {
        val m = RoverMotion().step(1f, tracking = true, routeAvailable = true)
        assertEquals(MotionStatus.IDLE, m.status)
        assertEquals(FloorPoint.ORIGIN, m.pose.position)
    }

    @Test
    fun `drives along the route at its speed and arrives`() {
        val started = RoverMotion().start(straightAhead)
        val halfway = started.run(2f)
        assertEquals(MotionStatus.DRIVING, halfway.status)
        assertEquals(-0.3f, halfway.pose.position.z, 0.02f)
        assertEquals(0.3f, halfway.progress, 0.02f)

        val done = halfway.run(3f)
        assertEquals(MotionStatus.ARRIVED, done.status)
        // Stops just short of the destination marker, not on top of it.
        assertEquals(0f, done.pose.position.x, 1e-4f)
        assertEquals(-0.6f + ARRIVAL_DISTANCE, done.pose.position.z, 1e-3f)
        assertFalse(done.isRunning)
    }

    @Test
    fun `turns in place before driving when facing away`() {
        val behind = listOf(FloorPoint.ORIGIN, FloorPoint(0f, 0.6f)) // target is behind (+Z)
        val m = RoverMotion().start(behind).run(0.5f)
        assertEquals(MotionStatus.TURNING, m.status)
        assertEquals(FloorPoint.ORIGIN, m.pose.position, "no driving while turning")

        val turned = m.run(1.5f)
        assertTrue(abs(abs(turned.pose.heading) - 180f) < 1f, "heading ${turned.pose.heading}")
        assertTrue(turned.pose.position.z > 0f, "then drives towards +Z")
    }

    @Test
    fun `follows corners in order`() {
        val corner = listOf(FloorPoint.ORIGIN, FloorPoint(0f, -0.3f), FloorPoint(0.3f, -0.3f))
        val done = RoverMotion().start(corner).run(10f)
        assertEquals(MotionStatus.ARRIVED, done.status)
        assertEquals(0.3f - ARRIVAL_DISTANCE, done.pose.position.x, 1e-3f)
        assertEquals(-0.3f, done.pose.position.z, 1e-4f)
        // Faces +X at the end: heading of -90 degrees.
        assertEquals(headingDegrees(FloorPoint(0f, -0.3f), FloorPoint(0.3f, -0.3f)), done.pose.heading, 1f)
    }

    @Test
    fun `pauses without tracking or route and resumes`() {
        val moving = RoverMotion().start(straightAhead).run(1f)
        val lost = moving.step(0.5f, tracking = false, routeAvailable = true)
        assertEquals(MotionStatus.PAUSED, lost.status)
        assertEquals(PauseReason.TRACKING_LOST, lost.pauseReason)
        assertEquals(moving.pose, lost.pose)

        val unseen = moving.step(0.5f, tracking = true, routeAvailable = false)
        assertEquals(PauseReason.NO_ROUTE, unseen.pauseReason)
        assertEquals(moving.pose, unseen.pose)

        val resumed = lost.run(0.5f)
        assertEquals(MotionStatus.DRIVING, resumed.status)
        assertTrue(resumed.pose.position.z < moving.pose.position.z)
    }

    @Test
    fun `a replanned route is followed from the current pose`() {
        val moving = RoverMotion().start(straightAhead).run(1f)
        val detour = listOf(moving.pose.position, FloorPoint(0.2f, moving.pose.position.z - 0.2f))
        val replanned = moving.withRoute(detour)
        assertEquals(1, replanned.target)
        assertEquals(MotionStatus.ARRIVED, replanned.run(5f).status)
        assertEquals(moving, moving.withRoute(straightAhead), "same route changes nothing")
    }

    @Test
    fun `stop waits for the player where the rover is`() {
        val moving = RoverMotion().start(straightAhead).run(1f)
        val stopped = moving.stop()
        assertEquals(MotionStatus.IDLE, stopped.status)
        assertEquals(stopped, stopped.run(1f))
    }

    @Test
    fun `never stops on the flag when a corner is right next to it`() {
        // The last corner is 5 cm from the destination: the rover must stop before that corner.
        val destination = FloorPoint(0.05f, -0.6f)
        val route = listOf(FloorPoint.ORIGIN, FloorPoint(0f, -0.6f), destination)
        val done = RoverMotion().start(route).run(10f)
        assertEquals(MotionStatus.ARRIVED, done.status)
        assertEquals(ARRIVAL_DISTANCE, done.pose.position.distanceTo(destination), 2e-3f)
    }

    @Test
    fun `stops the first time the route passes close to the destination`() {
        // The route swings past the flag before its last corner.
        val destination = FloorPoint(0.15f, -0.5f)
        val route = listOf(FloorPoint.ORIGIN, FloorPoint(0f, -1f), FloorPoint(0.15f, -1f), destination)
        val done = RoverMotion().start(route).run(20f)
        assertEquals(MotionStatus.ARRIVED, done.status)
        assertEquals(ARRIVAL_DISTANCE, done.pose.position.distanceTo(destination), 2e-3f)
        assertTrue(done.pose.position.x < 0.01f, "stopped on the first leg that came close: ${done.pose.position}")
    }

    @Test
    fun `angles are normalized to the shortest turn`() {
        assertEquals(-10f, angleDifference(350f, 0f), 1e-4f)
        assertEquals(20f, angleDifference(10f, -10f), 1e-4f)
        assertEquals(180f, normalizeDegrees(-180f), 1e-4f)
    }

    @Test
    fun `routes are planned from where the rover is now`() {
        val ready = (FloorSetup().confirmArea().withRover().placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        val route = planRoute(ready, null, rover = FloorPoint(0.5f, 0.2f)) as RoutePlan.Route
        assertEquals(FloorPoint(0.5f, 0.2f), route.points.first())
        assertEquals(FloorPoint(1f, 0f), route.points.last())
    }

    @Test
    fun `hints follow the drive`() {
        val ready = (FloorSetup().confirmArea().withRover().placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        fun hint(driving: Boolean = false, arrived: Boolean = false, needsScan: Boolean = false) =
            setupHint(ready, true, false, true, true, routeNeedsScan = needsScan, driving = driving, arrived = arrived)
        assertEquals(SetupHint.READY, hint())
        assertEquals(SetupHint.DRIVING, hint(driving = true))
        assertEquals(SetupHint.NEEDS_SCAN, hint(driving = true, needsScan = true))
        assertEquals(SetupHint.ARRIVED, hint(arrived = true))
    }

    @Test
    fun `greeting hint shows while the rover greets, unless tracking is lost`() {
        val placed = FloorSetup().confirmArea().withRover()
        assertEquals(SetupHint.GREETING, setupHint(placed, true, false, true, true, greeting = true))
        assertEquals(SetupHint.TRACKING_LOST, setupHint(placed, false, false, true, true, greeting = true))
    }
}
