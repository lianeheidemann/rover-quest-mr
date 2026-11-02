package io.github.lianeheidemann.roverquest.core.nav

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoverDanceTest {

    private fun frames(dance: RoverDance): List<RoverDance> {
        var d = dance
        val all = mutableListOf(d)
        while (!d.done) {
            d = d.step(1f / 60, tracking = true)
            all += d
        }
        return all
    }

    @Test
    fun `turns to the player, wiggles, spins and hops facing the player`() {
        val facing = 120f
        val all = frames(RoverDance(from = -30f, facing = facing))
        assertEquals(-30f, all.first().heading, 1e-3f)

        val offsets = all.map { angleDifference(it.heading, facing) }
        assertTrue(offsets.any { it > DANCE_WIGGLE_DEGREES * 0.9f }, "wiggles left")
        assertTrue(offsets.any { it < -DANCE_WIGGLE_DEGREES * 0.9f }, "wiggles right")
        assertTrue(offsets.any { abs(it) > 170f }, "spins round")

        // Every hop happens with the face towards the player.
        val hopping = all.filter { it.lift > 0f }
        assertTrue(hopping.any { it.lift > DANCE_HOP_HEIGHT * 0.9f }, "hops")
        assertTrue(hopping.all { abs(angleDifference(it.heading, facing)) < 1e-3f }, "faces the player while hopping")
        assertTrue(all.all { it.lift >= 0f }, "never sinks into the floor")

        val end = all.last()
        assertTrue(end.done)
        assertEquals(facing, end.heading, 1e-3f)
        assertEquals(0f, end.lift)
    }

    @Test
    fun `waits while tracking is lost`() {
        val d = RoverDance().step(0.5f, tracking = true)
        assertEquals(d, d.step(0.5f, tracking = false))
        assertFalse(d.done)
    }
}
