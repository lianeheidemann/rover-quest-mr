package io.github.lianeheidemann.roverquest.core.floor

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadingTest {

    /** Rotating the front (0, 0, -1) by [degrees] around +Y, as the renderer does. */
    private fun front(degrees: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees.toDouble())
        return (-sin(r)).toFloat() to (-cos(r)).toFloat()
    }

    @Test
    fun `heading turns the front towards the target`() {
        for (target in listOf(FloorPoint(0f, -1f), FloorPoint(1f, 0f), FloorPoint(-1f, 0f), FloorPoint(0f, 1f), FloorPoint(1f, 1f))) {
            val (fx, fz) = front(headingDegrees(FloorPoint.ORIGIN, target))
            val length = kotlin.math.hypot(target.x, target.z)
            assertEquals(target.x / length, fx, 1e-5f, "x for $target")
            assertEquals(target.z / length, fz, 1e-5f, "z for $target")
        }
    }

    @Test
    fun `no target keeps the rover facing ahead`() {
        assertEquals(0f, headingDegrees(FloorPoint.ORIGIN, FloorPoint.ORIGIN))
        assertEquals(0f, roverHeading(FloorSetup(), null))
    }

    @Test
    fun `the rover faces the first turn of the route, else the destination`() {
        val ready = (FloorSetup().confirmArea().withRover().placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(headingDegrees(FloorPoint.ORIGIN, FloorPoint(1f, 0f)), roverHeading(ready, null))
        val route = listOf(FloorPoint.ORIGIN, FloorPoint(0f, -0.5f), FloorPoint(1f, 0f))
        assertEquals(headingDegrees(FloorPoint.ORIGIN, FloorPoint(0f, -0.5f)), roverHeading(ready, route))
    }
}
