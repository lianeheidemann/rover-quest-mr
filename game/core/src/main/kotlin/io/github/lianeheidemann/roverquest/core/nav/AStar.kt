package io.github.lianeheidemann.roverquest.core.nav

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class NoPathReason { OUT_OF_GRID, START_BLOCKED, GOAL_BLOCKED, UNREACHABLE }

sealed interface PathResult {
    /** Cells from start to goal, both included. */
    data class Found(val cells: List<Cell>) : PathResult
    data class NotFound(val reason: NoPathReason) : PathResult
}

private val SQRT2 = sqrt(2.0)

private val NEIGHBORS = listOf(
    1 to 0, -1 to 0, 0 to 1, 0 to -1,
    1 to 1, 1 to -1, -1 to 1, -1 to -1,
)

/**
 * A* over the grid with 8 neighbors. Diagonal moves are only allowed when both orthogonal
 * neighbors are passable, so the path never cuts an obstacle's corner.
 *
 * @param allowUnknown whether [CellState.UNKNOWN] cells can be crossed. [CellState.OCCUPIED]
 *   cells never can.
 */
fun findPath(grid: OccupancyGrid, start: Cell, goal: Cell, allowUnknown: Boolean = false): PathResult {
    if (start !in grid || goal !in grid) return PathResult.NotFound(NoPathReason.OUT_OF_GRID)

    fun passable(cell: Cell): Boolean = cell in grid && when (grid[cell]) {
        CellState.FREE -> true
        CellState.UNKNOWN -> allowUnknown
        CellState.OCCUPIED -> false
    }

    if (!passable(start)) return PathResult.NotFound(NoPathReason.START_BLOCKED)
    if (!passable(goal)) return PathResult.NotFound(NoPathReason.GOAL_BLOCKED)

    fun heuristic(cell: Cell): Double {
        val dx = abs(cell.col - goal.col)
        val dz = abs(cell.row - goal.row)
        return max(dx, dz) + (SQRT2 - 1) * min(dx, dz)
    }

    data class Entry(val cell: Cell, val f: Double, val order: Long)

    var counter = 0L
    val open = PriorityQueue<Entry>(compareBy<Entry> { it.f }.thenBy { it.order })
    val cost = HashMap<Cell, Double>()
    val cameFrom = HashMap<Cell, Cell>()
    val closed = HashSet<Cell>()

    cost[start] = 0.0
    open.add(Entry(start, heuristic(start), counter++))

    while (open.isNotEmpty()) {
        val current = open.poll().cell
        if (current == goal) return PathResult.Found(rebuild(cameFrom, goal))
        if (!closed.add(current)) continue

        for ((dc, dr) in NEIGHBORS) {
            val next = Cell(current.col + dc, current.row + dr)
            if (!passable(next) || next in closed) continue
            val diagonal = dc != 0 && dr != 0
            if (diagonal &&
                (!passable(Cell(current.col + dc, current.row)) || !passable(Cell(current.col, current.row + dr)))
            ) {
                continue
            }
            val tentative = cost.getValue(current) + if (diagonal) SQRT2 else 1.0
            if (tentative < (cost[next] ?: Double.POSITIVE_INFINITY)) {
                cost[next] = tentative
                cameFrom[next] = current
                open.add(Entry(next, tentative + heuristic(next), counter++))
            }
        }
    }
    return PathResult.NotFound(NoPathReason.UNREACHABLE)
}

private fun rebuild(cameFrom: Map<Cell, Cell>, goal: Cell): List<Cell> {
    val path = ArrayList<Cell>()
    var cell: Cell? = goal
    while (cell != null) {
        path.add(cell)
        cell = cameFrom[cell]
    }
    return path.asReversed()
}
