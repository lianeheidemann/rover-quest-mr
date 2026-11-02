package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.PlacementResult
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutePlannerTest {

    private fun FloorSetup.destination(x: Float, z: Float) =
        (placeDestination(x, 0f, z) as PlacementResult.Accepted).setup

    private fun FloorSetup.obstacle(x: Float, z: Float) =
        (placeObstacle(x, 0f, z) as PlacementResult.Accepted).setup

    private val rover = FloorSetup().confirmArea().withRover()

    @Test
    fun `no route before the destination is placed`() {
        assertNull(planRoute(FloorSetup()))
        assertNull(planRoute(rover))
    }

    @Test
    fun `route without obstacle is a straight line`() {
        val route = planRoute(rover.destination(1f, 0f))
        assertIs<RoutePlan.Route>(route)
        assertEquals(FloorPoint.ORIGIN, route.points.first())
        assertEquals(FloorPoint(1f, 0f), route.points.last())
        assertTrue(route.length < 1.1f, "length ${route.length}")
    }

    @Test
    fun `route goes around the manual obstacle`() {
        val setup = rover.destination(1.2f, 0f).obstacle(0.6f, 0f)
        val route = planRoute(setup)
        assertIs<RoutePlan.Route>(route)
        assertTrue(route.length > 1.2f, "must detour, length ${route.length}")

        // Every point along the route keeps the rover's center out of the inflated obstacle.
        val obstacle = setup.obstacle!!
        val dots = samplePoints(route.points, 0.01f)
        assertTrue(dots.none { FloorSetup.obstacleBlocks(obstacle, it) && insideStrict(obstacle, it) })
    }

    private fun insideStrict(obstacle: FloorPoint, p: FloorPoint): Boolean {
        // Allow half a cell of tolerance: the path follows cell centers.
        val limit = FloorSetup.OBSTACLE_BLOCKED_HALF_SIZE - ROUTE_CELL_SIZE / 2
        return abs(p.x - obstacle.x) < limit && abs(p.z - obstacle.z) < limit
    }

    @Test
    fun `an obstacle accepted right next to the rover does not seal it in`() {
        // 26 cm from the rover: accepted (limit 25 cm), but its inflated area reaches the
        // rover's 5 cm cell.
        val setup = rover.destination(-1f, 0f).obstacle(0.26f, 0f)
        assertIs<RoutePlan.Route>(planRoute(setup))
        val withMap = planRoute(setup, newObstacleMap())
        assertTrue(withMap !is RoutePlan.Blocked || withMap.reason != NoPathReason.START_BLOCKED)
    }

    @Test
    fun `grid marks the inflated obstacle as occupied`() {
        val grid = buildGrid(rover.obstacle(1f, 1f))
        assertEquals(CellState.OCCUPIED, grid[grid.cellAt(FloorPoint(1f, 1f))!!])
        assertEquals(CellState.OCCUPIED, grid[grid.cellAt(FloorPoint(1.2f, 1f))!!])
        assertEquals(CellState.UNKNOWN, grid[grid.cellAt(FloorPoint(1.4f, 1f))!!])
    }

    /** A depth map that saw free floor everywhere around, and something tall over [blockedX]. */
    private fun mapWithWall(blockedX: ClosedFloatingPointRange<Float>): ObstacleMap {
        val map = newObstacleMap()
        val samples = ArrayList<io.github.lianeheidemann.roverquest.core.depth.FloorSample>()
        for (i in -30..40) for (j in -20..20) {
            // Cell centers, so every cell gets its samples.
            val x = (i + 0.5f) * ROUTE_CELL_SIZE
            val z = (j + 0.5f) * ROUTE_CELL_SIZE
            val height = if (x in blockedX) 0.2f else 0f
            repeat(2) { samples += io.github.lianeheidemann.roverquest.core.depth.FloorSample(x, z, height) }
        }
        repeat(5) { map.integrate(samples) }
        return map
    }

    @Test
    fun `a destination inside a seen obstacle is reached from the nearest open floor`() {
        // Furniture from x = 1.0 m on; the destination was marked right at its edge.
        val map = mapWithWall(1.0f..2.0f)
        val setup = rover.destination(1.05f, 0f)
        val route = planRoute(setup, map)
        assertIs<RoutePlan.Route>(route)

        // The route stays on open floor (the rover's clearance keeps it ~10 cm off the furniture)...
        val grid = buildGrid(setup, map)
        val stops = route.points.dropLast(1)
        assertTrue(stops.all { grid[grid.cellAt(it)!!] != CellState.OCCUPIED }, "stops on open floor: $stops")
        // ...and ends with a short stub pointing at the destination.
        val spot = stops.last()
        assertTrue(spot.distanceTo(FloorPoint(1.05f, 0f)) <= GOAL_SNAP_RADIUS)
        assertTrue(route.points.last().distanceTo(spot) <= ARRIVAL_DISTANCE + 1e-4f)

        // The rover then stops on that open spot.
        var motion = RoverMotion().start(route.points)
        repeat(60 * 20) { motion = motion.step(1f / 60, tracking = true, routeAvailable = true) }
        assertEquals(MotionStatus.ARRIVED, motion.status)
        assertTrue(motion.pose.position.distanceTo(spot) < 0.01f, "stopped at ${motion.pose.position}, spot $spot")
    }

    @Test
    fun `a destination deep inside an obstacle is still refused`() {
        val map = mapWithWall(0.6f..2.0f)
        val route = planRoute(rover.destination(1.5f, 0f), map)
        assertEquals(RoutePlan.Blocked(NoPathReason.GOAL_BLOCKED), route)
    }

    @Test
    fun `corners keep only direction changes`() {
        val cells = listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 1), Cell(4, 2), Cell(4, 3))
        assertEquals(listOf(Cell(0, 0), Cell(2, 0), Cell(4, 2), Cell(4, 3)), corners(cells))
    }

    @Test
    fun `samples are evenly spaced and skip the ends`() {
        val points = listOf(FloorPoint(0f, 0f), FloorPoint(1f, 0f), FloorPoint(1f, 1f))
        val samples = samplePoints(points, 0.5f)
        assertEquals(3, samples.size)
        assertEquals(0.5f, samples[0].x, 1e-5f)
        assertEquals(FloorPoint(1f, 0f), samples[1])
        assertEquals(0.5f, samples[2].z, 1e-5f)
    }
}
