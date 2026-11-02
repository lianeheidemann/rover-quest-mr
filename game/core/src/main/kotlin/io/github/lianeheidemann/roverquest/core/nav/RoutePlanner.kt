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

/**
 * A destination that depth sees inside an obstacle (often right next to furniture) is moved to the
 * nearest open floor within this distance, in meters, instead of being refused.
 */
const val GOAL_SNAP_RADIUS = 0.30f

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
fun buildGrid(setup: FloorSetup, map: ObstacleMap? = null, rover: FloorPoint = FloorPoint.ORIGIN): OccupancyGrid {
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
        freeAround(grid, map, rover)
        setup.destination?.let { freeAround(grid, map, it) }
    }
    setup.obstacle?.let { obstacle ->
        // Inflated by the rover's radius so the rover's center can follow the path safely.
        val half = FloorSetup.OBSTACLE_BLOCKED_HALF_SIZE
        grid.markRect(obstacle, half, half, CellState.OCCUPIED)
        // The rover and the destination really are on these cells: the obstacle, accepted
        // next to them, must not seal them in because of the 5 cm grid rounding.
        for (spot in listOfNotNull(rover, setup.destination)) {
            val cell = grid.cellAt(spot) ?: continue
            if (map?.stateOf(cell) != CellState.OCCUPIED) grid[cell] = if (map == null) CellState.UNKNOWN else CellState.FREE
        }
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
 * Plans a route from the rover (at [rover], the anchor origin until it drives) to the destination,
 * or returns `null` while the destination is not placed.
 *
 * Without a depth [map] (depth unsupported), unknown cells are crossed as if free and only the
 * manual obstacle blocks the rover. With a map, the route only crosses floor that depth has seen
 * free; when that is not enough but a way through unseen cells exists, the result is
 * [RoutePlan.NeedsScan].
 */
fun planRoute(setup: FloorSetup, map: ObstacleMap? = null, rover: FloorPoint = FloorPoint.ORIGIN): RoutePlan? {
    val destination = setup.destination ?: return null
    if (!setup.roverPlaced) return null
    val grid = buildGrid(setup, map, rover)
    val start = grid.cellAt(rover) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)
    val marked = grid.cellAt(destination) ?: return RoutePlan.Blocked(NoPathReason.OUT_OF_GRID)
    // Next to furniture the destination's own cell can be blocked: aim for the closest open floor
    // the rover can reach instead.
    var goal = marked
    if (grid[marked] == CellState.OCCUPIED) {
        val reachable = reachableFrom(grid, start, allowUnknown = map == null)
        val spot = openCellsNear(grid, destination).firstOrNull { it in reachable }
        if (spot != null) {
            val path = findPath(grid, start, spot, allowUnknown = map == null)
            if (path is PathResult.Found) return toSnappedRoute(grid, path.cells, rover, destination)
        }
        // Nothing reachable yet: an open spot behind unseen floor still asks for a scan.
        goal = openCellsNear(grid, destination).firstOrNull() ?: marked
    }

    val strict = findPath(grid, start, goal, allowUnknown = map == null)
    return when (strict) {
        is PathResult.Found -> toRoute(grid, strict.cells, rover, destination)
        is PathResult.NotFound -> {
            val throughUnknown = map != null && strict.reason == NoPathReason.UNREACHABLE &&
                findPath(grid, start, goal, allowUnknown = true) is PathResult.Found
            if (throughUnknown) RoutePlan.NeedsScan else RoutePlan.Blocked(strict.reason)
        }
    }
}

private fun toRoute(grid: OccupancyGrid, cells: List<Cell>, rover: FloorPoint, destination: FloorPoint): RoutePlan.Route {
    val corners = corners(cells).map(grid::centerOf)
    // Start and end exactly at the rover and the destination, not at cell centers.
    val inner = corners.drop(1).dropLast(1)
    return RoutePlan.Route(listOf(rover) + inner + destination)
}

/** Cells the rover can reach from [start] moving through passable cells (8 neighbors). */
private fun reachableFrom(grid: OccupancyGrid, start: Cell, allowUnknown: Boolean): Set<Cell> {
    fun passable(cell: Cell) = cell in grid && when (grid[cell]) {
        CellState.FREE -> true
        CellState.UNKNOWN -> allowUnknown
        CellState.OCCUPIED -> false
    }
    val seen = HashSet<Cell>()
    if (!passable(start)) return seen
    val queue = ArrayDeque(listOf(start))
    seen.add(start)
    while (queue.isNotEmpty()) {
        val cell = queue.removeFirst()
        for (dr in -1..1) for (dc in -1..1) {
            val next = Cell(cell.col + dc, cell.row + dr)
            if (next !in seen && passable(next)) {
                seen.add(next)
                queue.addLast(next)
            }
        }
    }
    return seen
}

/** Non-blocked cells within [GOAL_SNAP_RADIUS] of [point], nearest first, seen free floor first. */
private fun openCellsNear(grid: OccupancyGrid, point: FloorPoint): List<Cell> {
    val center = grid.cellAt(point) ?: return emptyList()
    val reach = ceil(GOAL_SNAP_RADIUS / ROUTE_CELL_SIZE).toInt()
    val found = ArrayList<Pair<Cell, Float>>()
    for (dr in -reach..reach) {
        for (dc in -reach..reach) {
            val cell = Cell(center.col + dc, center.row + dr)
            if (cell !in grid || grid[cell] == CellState.OCCUPIED) continue
            val distance = grid.centerOf(cell).distanceTo(point)
            if (distance > GOAL_SNAP_RADIUS) continue
            found += cell to distance + if (grid[cell] == CellState.FREE) 0f else GOAL_SNAP_RADIUS
        }
    }
    return found.sortedBy { it.second }.map { it.first }
}

/**
 * Route to the open spot next to a blocked destination. It ends with a short stub towards the
 * destination, [ARRIVAL_DISTANCE] long at most, so the rover stops on the open spot facing the
 * destination instead of driving into what blocks it.
 */
private fun toSnappedRoute(grid: OccupancyGrid, cells: List<Cell>, rover: FloorPoint, destination: FloorPoint): RoutePlan.Route {
    val corners = corners(cells).map(grid::centerOf)
    val spot = corners.last()
    val gap = spot.distanceTo(destination)
    val end = if (gap <= ARRIVAL_DISTANCE) destination else {
        val t = ARRIVAL_DISTANCE / gap
        FloorPoint(spot.x + (destination.x - spot.x) * t, spot.z + (destination.z - spot.z) * t)
    }
    val inner = corners.drop(1)
    return RoutePlan.Route(listOf(rover) + inner + end)
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
