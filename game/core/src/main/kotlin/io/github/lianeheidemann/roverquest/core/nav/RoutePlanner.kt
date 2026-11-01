package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup

sealed interface RoutePlan {
    /** Corners of the route on the floor, from the rover to the destination. */
    data class Route(val points: List<FloorPoint>) : RoutePlan {
        val length: Float get() = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()
    }

    data class Blocked(val reason: NoPathReason) : RoutePlan
}

/** Grid resolution used for route planning. */
const val ROUTE_CELL_SIZE = 0.05f

/** Builds the occupancy grid for [setup]: only the manual obstacle is known so far. */
fun buildGrid(setup: FloorSetup): OccupancyGrid {
    val grid = OccupancyGrid(ROUTE_CELL_SIZE, FloorSetup.PLAY_AREA_HALF_SIZE)
    setup.obstacle?.let { obstacle ->
        // Inflated by the rover's radius so the rover's center can follow the path safely.
        val half = FloorSetup.OBSTACLE_BLOCKED_HALF_SIZE
        grid.markRect(obstacle, half, half, CellState.OCCUPIED)
    }
    return grid
}

/**
 * Plans a route from the rover to the destination, or returns `null` while the destination is
 * not placed.
 *
 * Real obstacles are not detected yet, so every other cell stays [CellState.UNKNOWN] and is
 * crossed as if it were free. Only the manual obstacle blocks the rover.
 */
fun planRoute(setup: FloorSetup): RoutePlan? {
    val destination = setup.destination ?: return null
    if (!setup.roverPlaced) return null
    val grid = buildGrid(setup)
    val start = grid.cellAt(FloorPoint.ORIGIN) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)
    val goal = grid.cellAt(destination) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)

    return when (val result = findPath(grid, start, goal, allowUnknown = true)) {
        is PathResult.NotFound -> RoutePlan.Blocked(result.reason)
        is PathResult.Found -> {
            val corners = corners(result.cells).map(grid::centerOf)
            // Start and end exactly at the rover and the destination, not at cell centers.
            val inner = corners.drop(1).dropLast(1)
            RoutePlan.Route(listOf(FloorPoint.ORIGIN) + inner + destination)
        }
    }
}

/** Keeps the first and last cells and every cell where the direction changes. */
internal fun corners(cells: List<Cell>): List<Cell> {
    if (cells.size <= 2) return cells
    val result = mutableListOf(cells.first())
    for (i in 1 until cells.lastIndex) {
        val before = direction(cells[i - 1], cells[i])
        val after = direction(cells[i], cells[i + 1])
        if (before != after) result.add(cells[i])
    }
    result.add(cells.last())
    return result
}

private fun direction(from: Cell, to: Cell) = (to.col - from.col) to (to.row - from.row)

/**
 * Points every [spacing] meters along the route, for drawing it as dots. The rover and the
 * destination themselves are not included.
 */
fun samplePoints(points: List<FloorPoint>, spacing: Float): List<FloorPoint> {
    require(spacing > 0f) { "spacing must be positive" }
    val samples = mutableListOf<FloorPoint>()
    var nextAt = spacing
    var walked = 0f
    for ((a, b) in points.zipWithNext()) {
        val segment = a.distanceTo(b)
        while (segment > 0f && nextAt < walked + segment) {
            val t = (nextAt - walked) / segment
            samples.add(FloorPoint(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t))
            nextAt += spacing
        }
        walked += segment
    }
    return samples
}
