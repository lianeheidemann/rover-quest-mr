package io.github.lianeheidemann.roverquest.core.nav

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoverDanceTest {

    private fun frames(): List<RoverDance> {
        var d = RoverDance()
        val all = mutableListOf(d)
        while (!d.done) {
            d = d.step(1f / 60, tracking = true)
            all += d
        }
        return all
    }

    @Test
    fun `wiggles, spins all the way round and hops, then rests`() {
        val all = frames()
        assertTrue(all.any { it.turn > DANCE_WIGGLE_DEGREES * 0.9f }, "wiggles left")
        assertTrue(all.any { it.turn < -DANCE_WIGGLE_DEGREES * 0.9f }, "wiggles right")
        assertTrue(all.any { abs(it.turn) > 170f }, "spins half way and beyond")
        assertTrue(all.any { it.lift > DANCE_HOP_HEIGHT * 0.9f }, "hops")
        assertTrue(all.all { it.lift >= 0f }, "never sinks into the floor")

        val end = all.last()
        assertTrue(end.done)
        assertEquals(0f, end.turn)
        assertEquals(0f, end.lift)
    }

    @Test
    fun `waits while tracking is lost`() {
        val d = RoverDance().step(0.5f, tracking = true)
        assertEquals(d, d.step(0.5f, tracking = false))
        assertFalse(d.done)
    }
}
