package io.github.lianeheidemann.roverquest.core.depth

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.nav.CellState
import io.github.lianeheidemann.roverquest.core.nav.newObstacleMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloorLevelTest {

    /** Floor points every 5 cm over x in [-1, 1], z in [-2.5, 0], at [height] ± 1 cm. */
    private fun floor(height: (x: Float, z: Float) -> Float, seed: Int = 1): List<FloorSample> {
        val random = Random(seed)
        val samples = ArrayList<FloorSample>()
        for (i in 0..40) for (j in 0..50) {
            val x = -1f + i * 0.05f
            val z = -j * 0.05f
            // Two points per spot, as several depth pixels land on each map cell.
            repeat(2) { samples += FloorSample(x, z, height(x, z) + (random.nextFloat() - 0.5f) * 0.02f) }
        }
        return samples
    }

    /** A box top and sides, [top] high, over x in [x0, x0 + 0.3], z in [z0, z0 + 0.3]. */
    private fun box(x0: Float, z0: Float, top: Float, base: (Float, Float) -> Float = { _, _ -> 0f }): List<FloorSample> {
        val samples = ArrayList<FloorSample>()
        for (i in 0..6) for (j in 0..6) {
            val x = x0 + i * 0.05f
            val z = z0 + j * 0.05f
            repeat(3) { samples += FloorSample(x, z, base(x, z) + top) }
        }
        return samples
    }

    private fun stateAt(samples: List<FloorSample>, x: Float, z: Float): CellState {
        val map = newObstacleMap()
        repeat(4) { map.integrate(samples) }
        return map.stateOf(map.cellAt(FloorPoint(x, z))!!)
    }

    @Test
    fun `floor a few centimeters above the anchor stays free`() {
        val raw = floor({ _, _ -> 0.06f })
        // Without leveling, the whole floor would be an obstacle (the bug seen on the device).
        assertEquals(CellState.OCCUPIED, stateAt(raw, 0f, -2f))

        val level = FloorLevel()
        val leveled = level.level(raw)
        assertEquals(0.06f, level.a, 0.01f)
        assertEquals(CellState.FREE, stateAt(leveled, 0f, -2f))
        assertEquals(CellState.FREE, stateAt(leveled, 0.5f, -0.5f))
    }

    @Test
    fun `tilted floor stays free and a box on it is still an obstacle`() {
        val tilt = { _: Float, z: Float -> -z * 0.025f } // rises 5 cm every 2 m away from the anchor
        val samples = floor(tilt) + box(0.3f, -1.8f, top = 0.2f, base = tilt)
        val leveled = FloorLevel().level(samples)
        assertEquals(CellState.FREE, stateAt(leveled, -0.5f, -2.4f))
        assertEquals(CellState.OCCUPIED, stateAt(leveled, 0.45f, -1.65f))
    }

    @Test
    fun `a big box does not pull the floor up`() {
        val boxes = (0 until 6).flatMap { k -> box(-0.9f + k * 0.3f, -1.2f, top = 0.12f) } +
            (0 until 6).flatMap { k -> box(-0.9f + k * 0.3f, -0.9f, top = 0.12f) }
        val level = FloorLevel()
        level.level(floor({ _, _ -> 0f }) + boxes + boxes)
        assertEquals(0f, level.a, 0.01f)
        assertEquals(0f, level.c, 0.01f)
    }

    @Test
    fun `keeps the last floor when too little of it is seen`() {
        val level = FloorLevel()
        level.level(floor({ _, _ -> 0.05f }))
        val fitted = level.a
        // Only a box in view: the floor stays where it was.
        level.level(box(0f, -1f, top = 0.2f))
        assertEquals(fitted, level.a)
    }

    @Test
    fun `a steep surface is not taken for the floor`() {
        val level = FloorLevel()
        level.level(floor({ _, z -> -z * 0.3f }))
        assertEquals(0f, level.a)
        assertEquals(0f, level.c)
        level.reset()
        assertTrue(level.a == 0f && level.b == 0f && level.c == 0f)
    }
}
