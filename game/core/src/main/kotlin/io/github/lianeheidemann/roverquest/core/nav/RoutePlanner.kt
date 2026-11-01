package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import kotlin.math.ceil

sealed interface RoutePlan {
    /** Corners of the route on the floor, from the rover to the destination. */
    data class Route(val points: List<FloorPoint>) : RoutePlan {
        val length: Float get() = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()
    }

    /** No route through known free floor yet, but parts of the way were never observed. */
    data object NeedsScan : RoutePlan

    data class Blocked(val reason: NoPathReason) : RoutePlan
}

/** Grid resolution used for route planning. */
const val ROUTE_CELL_SIZE = 0.05f

/** An empty depth map on the same grid as route planning. */
fun newObstacleMap(): ObstacleMap = ObstacleMap(ROUTE_CELL_SIZE, FloorSetup.PLAY_AREA_HALF_SIZE)

/**
 * Builds the planning grid for [setup].
 *
 * With a depth [map], its free and occupied cells are copied and every occupied cell is grown by
 * the rover's radius, so the rover's center can follow the path without touching anything.
 * The spots under the rover and the destination count as free floor: both were placed on the
 * detected floor. Without a map every cell stays unknown. The manual obstacle is always added.
 */
fun buildGrid(setup: FloorSetup, map: ObstacleMap? = null): OccupancyGrid {
    val grid = OccupancyGrid(ROUTE_CELL_SIZE, FloorSetup.PLAY_AREA_HALF_SIZE)
    if (map != null) {
        require(map.size == grid.size) { "map and planning grid must match" }
        val occupied = ArrayList<Cell>()
        for (row in 0 until grid.size) {
            for (col in 0 until grid.size) {
                val cell = Cell(col, row)
                val state = map.stateOf(cell)
                if (state == CellState.OCCUPIED) occupied.add(cell) else grid[cell] = state
            }
        }
        val reach = ceil(FloorSetup.ROVER_RADIUS / ROUTE_CELL_SIZE).toInt()
        for (cell in occupied) {
            for (dr in -reach..reach) {
                for (dc in -reach..reach) {
                    if (dc * dc + dr * dr > reach * reach) continue
                    val near = Cell(cell.col + dc, cell.row + dr)
                    if (near in grid) grid[near] = CellState.OCCUPIED
                }
            }
        }
        // Placed on the floor, so known free unless depth saw something right there. Done after
        // inflation: an obstacle next to the rover or the destination must not seal them in.
        freeAround(grid, map, FloorPoint.ORIGIN)
        setup.destination?.let { freeAround(grid, map, it) }
    }
    setup.obstacle?.let { obstacle ->
        // Inflated by the rover's radius so the rover's center can follow the path safely.
        val half = FloorSetup.OBSTACLE_BLOCKED_HALF_SIZE
        grid.markRect(obstacle, half, half, CellState.OCCUPIED)
    }
    return grid
}

private fun freeAround(grid: OccupancyGrid, map: ObstacleMap, point: FloorPoint) {
    val center = grid.cellAt(point) ?: return
    val reach = ceil(FloorSetup.ROVER_RADIUS / ROUTE_CELL_SIZE).toInt()
    for (dr in -reach..reach) {
        for (dc in -reach..reach) {
            val cell = Cell(center.col + dc, center.row + dr)
            if (cell in grid && map.stateOf(cell) != CellState.OCCUPIED) grid[cell] = CellState.FREE
        }
    }
}

/**
 * Plans a route from the rover to the destination, or returns `null` while the destination is
 * not placed.
 *
 * Without a depth [map] (depth unsupported), unknown cells are crossed as if free and only the
 * manual obstacle blocks the rover. With a map, the route only crosses floor that depth has seen
 * free; when that is not enough but a way through unseen cells exists, the result is
 * [RoutePlan.NeedsScan].
 */
fun planRoute(setup: FloorSetup, map: ObstacleMap? = null): RoutePlan? {
    val destination = setup.destination ?: return null
    if (!setup.roverPlaced) return null
    val grid = buildGrid(setup, map)
    val start = grid.cellAt(FloorPoint.ORIGIN) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)
    val goal = grid.cellAt(destination) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)

    val strict = findPath(grid, start, goal, allowUnknown = map == null)
    return when (strict) {
        is PathResult.Found -> toRoute(grid, strict.cells, destination)
        is PathResult.NotFound -> {
            val throughUnknown = map != null && strict.reason == NoPathReason.UNREACHABLE &&
                findPath(grid, start, goal, allowUnknown = true) is PathResult.Found
            if (throughUnknown) RoutePlan.NeedsScan else RoutePlan.Blocked(strict.reason)
        }
    }
}

private fun toRoute(grid: OccupancyGrid, cells: List<Cell>, destination: FloorPoint): RoutePlan.Route {
    val corners = corners(cells).map(grid::centerOf)
    // Start and end exactly at the rover and the destination, not at cell centers.
    val inner = corners.drop(1).dropLast(1)
    return RoutePlan.Route(listOf(FloorPoint.ORIGIN) + inner + destination)
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
