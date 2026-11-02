package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.depth.FloorSample
import io.github.lianeheidemann.roverquest.core.floor.FloorPoint

/** Points this high above the floor (meters) or more block the rover... */
const val MIN_OBSTACLE_HEIGHT = 0.05f

/** ...up to this height; anything higher (a table top) leaves room for the rover underneath. */
const val MAX_OBSTACLE_HEIGHT = 0.40f

/** Points within this distance of the floor height count as free floor. */
const val FLOOR_TOLERANCE = 0.03f

/** A cell needs this many points of one kind in a frame to get a vote. */
const val MIN_POINTS_PER_VOTE = 2

/** Votes needed to call a cell occupied (positive) or free (negative). */
const val VOTES_TO_DECIDE = 3

/** The score never goes past ±this, so a cell can still change its mind later. */
const val MAX_SCORE = 8

/**
 * Occupancy map built from depth over many frames, on the same grid as route planning.
 *
 * Each frame gives every observed cell at most one vote: obstacle (+1) or floor (-1). A cell only
 * becomes [CellState.OCCUPIED] or [CellState.FREE] after [VOTES_TO_DECIDE] net votes, and only
 * switches sides after crossing the opposite threshold, so one noisy frame changes nothing.
 * Cells never observed stay [CellState.UNKNOWN] — they are never assumed free.
 */
class ObstacleMap(cellSize: Float, halfSize: Float) {
    private val grid = OccupancyGrid(cellSize, halfSize)
    private val scores = IntArray(grid.size * grid.size)

    /** Grows every time [integrate] changes at least one cell's state. */
    var version: Int = 0
        private set

    val size: Int get() = grid.size

    fun cellAt(point: FloorPoint): Cell? = grid.cellAt(point)

    fun stateOf(cell: Cell): CellState = grid[cell]

    fun clear() {
        scores.fill(0)
        for (row in 0 until grid.size) for (col in 0 until grid.size) grid[Cell(col, row)] = CellState.UNKNOWN
        version++
    }

    /** Adds one frame of depth samples. Returns `true` when some cell changed state. */
    fun integrate(samples: List<FloorSample>): Boolean {
        val obstacleHits = HashMap<Int, Int>()
        val floorHits = HashMap<Int, Int>()
        for (s in samples) {
            val cell = grid.cellAt(FloorPoint(s.x, s.z)) ?: continue
            val key = cell.row * grid.size + cell.col
            when {
                s.height in MIN_OBSTACLE_HEIGHT..MAX_OBSTACLE_HEIGHT -> obstacleHits.merge(key, 1, Int::plus)
                s.height in -FLOOR_TOLERANCE..FLOOR_TOLERANCE -> floorHits.merge(key, 1, Int::plus)
                // Below the floor (reflections on shiny floors), between floor and obstacle
                // heights, or above the rover's reach: no vote.
            }
        }

        var changed = false
        val keys = obstacleHits.keys + floorHits.keys
        for (key in keys) {
            val vote = when {
                (obstacleHits[key] ?: 0) >= MIN_POINTS_PER_VOTE -> 1
                (floorHits[key] ?: 0) >= MIN_POINTS_PER_VOTE -> -1
                else -> 0
            }
            if (vote == 0) continue
            val score = (scores[key] + vote).coerceIn(-MAX_SCORE, MAX_SCORE)
            scores[key] = score
            val cell = Cell(col = key % grid.size, row = key / grid.size)
            val next = when {
                score >= VOTES_TO_DECIDE -> CellState.OCCUPIED
                score <= -VOTES_TO_DECIDE -> CellState.FREE
                else -> grid[cell] // between the thresholds: keep the previous decision
            }
            if (next != grid[cell]) {
                grid[cell] = next
                changed = true
            }
        }
        if (changed) version++
        return changed
    }
}
