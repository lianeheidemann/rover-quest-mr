package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoverGreetingTest {

    /** Plays the show at 60 frames per second and returns every pose. */
    private fun RoverGreeting.play(): List<RoverPose> {
        var g = this
        val poses = mutableListOf(g.pose)
        while (!g.done) {
            g = g.step(1f / 60, tracking = true)
            poses += g.pose
        }
        return poses
    }

    @Test
    fun `stops short of the player and never too far`() {
        val near = RoverGreeting.towards(FloorPoint(0f, 1f)) // player 1 m behind the rover (+Z)
        assertEquals(0f, near.approach.x, 1e-4f)
        assertEquals(1f - GREETING_STOP_SHORT, near.approach.z, 1e-4f)

        val far = RoverGreeting.towards(FloorPoint(0f, 3f))
        assertEquals(GREETING_MAX_DISTANCE, far.approach.z, 1e-4f)

        val tooClose = RoverGreeting.towards(FloorPoint(0f, 0.45f))
        assertEquals(FloorPoint.ORIGIN, tooClose.approach)
    }

    @Test
    fun `spins to the player, drives up, spins back and returns home facing ahead`() {
        val greeting = RoverGreeting.towards(FloorPoint(0f, 1.2f))
        val poses = greeting.play()

        // First half turn, in place.
        val afterTurn = greeting.copy(elapsed = 180f / ROVER_TURN_SPEED).pose
        assertEquals(FloorPoint.ORIGIN, afterTurn.position)
        assertEquals(180f, abs(afterTurn.heading), 1e-3f)

        // Gets close to the player.
        val closest = poses.maxOf { it.position.z }
        assertEquals(0.8f, closest, 1e-3f)

        // Ends where it was placed, facing ahead.
        val end = poses.last()
        assertEquals(FloorPoint.ORIGIN, end.position)
        assertEquals(0f, end.heading, 1e-3f)
    }

    @Test
    fun `faces the player while driving towards them`() {
        val greeting = RoverGreeting.towards(FloorPoint(1f, 1f))
        val start = 135f / ROVER_TURN_SPEED // a -135 degree turn (clockwise) faces (1, 1)
        val driving = greeting.copy(elapsed = start + 0.5f).pose
        assertTrue(driving.position.x > 0f && driving.position.z > 0f)
        assertEquals(-135f, driving.heading, 1e-3f)
    }

    @Test
    fun `waits while tracking is lost and can be skipped`() {
        val greeting = RoverGreeting.towards(FloorPoint(0f, 1f)).step(1f, tracking = true)
        assertEquals(greeting, greeting.step(1f, tracking = false))
        assertFalse(greeting.done)
        val skipped = greeting.finish()
        assertTrue(skipped.done)
        assertEquals(RoverPose(FloorPoint.ORIGIN, 0f), skipped.pose)
    }

    @Test
    fun `without room it still spins round and back`() {
        val poses = RoverGreeting(FloorPoint.ORIGIN).play()
        assertTrue(poses.all { it.position == FloorPoint.ORIGIN })
        assertTrue(poses.any { abs(it.heading) > 179f })
        assertEquals(0f, poses.last().heading, 1e-3f)
    }
}
