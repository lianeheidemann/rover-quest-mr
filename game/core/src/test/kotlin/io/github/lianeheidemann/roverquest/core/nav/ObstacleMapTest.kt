package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.depth.FloorSample
import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.PlacementResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ObstacleMapTest {

    private fun points(x: Float, z: Float, height: Float, count: Int = 3) =
        List(count) { FloorSample(x, z, height) }

    private fun ObstacleMap.state(x: Float, z: Float) = stateOf(cellAt(FloorPoint(x, z))!!)

    @Test
    fun `unobserved cells stay unknown`() {
        val map = newObstacleMap()
        map.integrate(points(1f, 1f, 0f))
        assertEquals(CellState.UNKNOWN, map.state(-1f, -1f))
    }

    @Test
    fun `one frame decides nothing, three consistent frames do`() {
        val map = newObstacleMap()
        repeat(2) { map.integrate(points(1f, 0f, 0.15f)) }
        assertEquals(CellState.UNKNOWN, map.state(1f, 0f))
        assertTrue(map.integrate(points(1f, 0f, 0.15f)))
        assertEquals(CellState.OCCUPIED, map.state(1f, 0f))

        repeat(3) { map.integrate(points(0.5f, 0f, 0.005f)) }
        assertEquals(CellState.FREE, map.state(0.5f, 0f))
    }

    @Test
    fun `occupied cells resist a few noisy floor frames`() {
        val map = newObstacleMap()
        repeat(5) { map.integrate(points(1f, 0f, 0.1f)) }
        repeat(3) { map.integrate(points(1f, 0f, 0f)) }
        assertEquals(CellState.OCCUPIED, map.state(1f, 0f))
        repeat(5) { map.integrate(points(1f, 0f, 0f)) }
        assertEquals(CellState.FREE, map.state(1f, 0f))
    }

    @Test
    fun `reflections below the floor, table tops and lone points do not vote`() {
        val map = newObstacleMap()
        repeat(5) {
            map.integrate(points(1f, 0f, -0.3f))        // reflection on a shiny floor
            map.integrate(points(0f, 1f, 0.75f))        // table top: rover passes under
            map.integrate(points(-1f, 0f, 0.1f, count = 1)) // a single noisy point
        }
        assertEquals(CellState.UNKNOWN, map.state(1f, 0f))
        assertEquals(CellState.UNKNOWN, map.state(0f, 1f))
        assertEquals(CellState.UNKNOWN, map.state(-1f, 0f))
        assertFalse(map.integrate(emptyList()))
    }

    @Test
    fun `clear forgets everything`() {
        val map = newObstacleMap()
        repeat(3) { map.integrate(points(1f, 0f, 0.1f)) }
        map.clear()
        assertEquals(CellState.UNKNOWN, map.state(1f, 0f))
    }

    // --- Planning with the map ---

    private val ready = ((FloorSetup().confirmArea().withRover().placeDestination(1.2f, 0f, 0f)) as PlacementResult.Accepted).setup

    /** Samples at the center of every cell in [xFrom, xTo] × [zFrom, zTo], two points each. */
    private fun cellCenters(xFrom: Float, xTo: Float, zFrom: Float, zTo: Float, height: Float): List<FloorSample> {
        val step = ROUTE_CELL_SIZE
        val samples = ArrayList<FloorSample>()
        for (i in 0..((xTo - xFrom) / step).toInt()) {
            for (j in 0..((zTo - zFrom) / step).toInt()) {
                val x = xFrom + i * step + step / 2
                val z = zFrom + j * step + step / 2
                repeat(2) { samples.add(FloorSample(x, z, height)) }
            }
        }
        return samples
    }

    /** Seen free floor around the rover and the destination: x in [-0.4, 1.6], z in [-0.8, 0.8]. */
    private fun ObstacleMap.seeFloor() {
        val samples = cellCenters(-0.4f, 1.6f, -0.8f, 0.8f, height = 0f)
        repeat(VOTES_TO_DECIDE) { integrate(samples) }
    }

    /** A real box about 20 cm wide around x = 0.6, seen long enough to win over the floor votes. */
    private fun ObstacleMap.seeBox() {
        val samples = cellCenters(0.5f, 0.65f, -0.1f, 0.05f, height = 0.15f)
        repeat(VOTES_TO_DECIDE * 2) { integrate(samples) }
    }

    @Test
    fun `route detours around a real obstacle seen by depth`() {
        val map = newObstacleMap().apply { seeFloor(); seeBox() }
        val route = planRoute(ready, map)
        assertIs<RoutePlan.Route>(route)
        assertTrue(route.length > 1.25f, "must detour, length ${route.length}")
        // Every point of the route keeps the rover's radius away from the box cells.
        val dots = samplePoints(route.points, 0.01f)
        assertTrue(dots.none { it.x in 0.5f..0.7f && kotlin.math.abs(it.z) < 0.1f + FloorSetup.ROVER_RADIUS - ROUTE_CELL_SIZE })
    }

    @Test
    fun `a destination right next to a real obstacle stays reachable`() {
        // Destination 15 cm from the box edge: inside the inflated area, but placed on the floor.
        val nearBox = (FloorSetup().confirmArea().withRover().placeDestination(0.8f, 0f, 0f) as PlacementResult.Accepted).setup
        val map = newObstacleMap().apply { seeFloor(); seeBox() }
        assertIs<RoutePlan.Route>(planRoute(nearBox, map))
    }

    @Test
    fun `unseen floor asks for a scan instead of assuming it is free`() {
        assertEquals(RoutePlan.NeedsScan, planRoute(ready, newObstacleMap()))
    }

    @Test
    fun `inflation grows real obstacles by the rover radius`() {
        val map = newObstacleMap().apply { seeFloor(); seeBox() }
        val grid = buildGrid(ready, map)
        assertEquals(CellState.OCCUPIED, grid[grid.cellAt(FloorPoint(0.6f, 0.17f))!!])
        assertEquals(CellState.FREE, grid[grid.cellAt(FloorPoint(0.6f, 0.4f))!!])
    }

    @Test
    fun `without a map the old manual-only behavior is kept`() {
        assertIs<RoutePlan.Route>(planRoute(ready, null))
    }

    @Test
    fun `debug colors tell real obstacles from clearance and unknown`() {
        val map = newObstacleMap().apply { seeFloor(); seeBox() }
        val grid = buildGrid(ready, map)
        val colors = gridColors(grid, map)
        fun colorAt(x: Float, z: Float): Int {
            val c = grid.cellAt(FloorPoint(x, z))!!
            return colors[c.row * grid.size + c.col]
        }
        assertEquals(COLOR_OCCUPIED, colorAt(0.6f, 0f))
        assertEquals(COLOR_CLEARANCE, colorAt(0.6f, 0.17f))
        assertEquals(COLOR_FREE, colorAt(1.4f, 0.5f))
        assertEquals(COLOR_UNKNOWN, colorAt(-2f, -2f))
    }
}
