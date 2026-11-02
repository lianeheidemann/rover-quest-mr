package io.github.lianeheidemann.roverquest.core.depth

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.nav.CellState
import io.github.lianeheidemann.roverquest.core.nav.newObstacleMap
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeLevelingTest {

    /** Camera 1 m up, 0.5 m behind the anchor; depth errors lift the floor by 1.2 cm per m² of distance. */
    private fun depthFrom(x: Float, z: Float, h: Float) = sqrt(x * x + (z - 0.5f) * (z - 0.5f) + (1f - h) * (1f - h))

    private fun biased(x: Float, z: Float, h: Float, random: Random): FloorSample {
        val d = depthFrom(x, z, h)
        return FloorSample(x, z, h + 0.012f * d * d + (random.nextFloat() - 0.5f) * 0.02f, depth = d)
    }

    /** Floor over x in [-1, 1], z in [-2.5, 0], plus a 20 cm box around ([boxX], [boxZ]). */
    private fun frame(boxX: Float = 0.3f, boxZ: Float = -1.2f): List<FloorSample> {
        val random = Random(3)
        val samples = ArrayList<FloorSample>()
        for (i in 0..40) for (j in 0..50) {
            val x = -1f + i * 0.05f + 0.025f
            val z = -j * 0.05f - 0.025f
            val box = kotlin.math.abs(x - boxX) < 0.15f && kotlin.math.abs(z - boxZ) < 0.15f
            repeat(2) {
                val s = biased(x, z, if (box) 0.2f else 0f, random)
                if (s.depth <= MAX_MAPPING_DEPTH) samples += s // as projectToFloor keeps them
            }
        }
        return samples
    }

    private fun stateAt(samples: List<FloorSample>, x: Float, z: Float): CellState {
        val map = newObstacleMap()
        repeat(4) { map.integrate(samples) }
        return map.stateOf(map.cellAt(FloorPoint(x, z))!!)
    }

    @Test
    fun `floor far from the camera stays free when depth errors grow with distance`() {
        val raw = frame()
        // Without the correction, the floor ~2.4 m from the camera comes out about 7 cm high.
        assertEquals(CellState.OCCUPIED, stateAt(raw, -0.325f, -1.675f))

        val level = FloorLevel()
        var leveled = raw
        repeat(40) { leveled = level.level(raw) }
        assertEquals(CellState.FREE, stateAt(leveled, -0.325f, -1.675f))
        assertEquals(CellState.FREE, stateAt(leveled, 0.6f, -0.3f))
        // A real box is still an obstacle.
        assertEquals(CellState.OCCUPIED, stateAt(leveled, 0.325f, -1.175f))
    }

    @Test
    fun `points without a known depth are left as they are`() {
        val samples = List(100) { FloorSample(0f, -1f, 0.03f) }
        assertEquals(samples, levelByRange(samples))
    }
}
