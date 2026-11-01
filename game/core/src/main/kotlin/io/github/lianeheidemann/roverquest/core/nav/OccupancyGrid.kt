package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class CellState { FREE, OCCUPIED, UNKNOWN }

data class Cell(val col: Int, val row: Int)

/**
 * Square grid on the floor, centered on the floor anchor. Columns follow +x and rows follow +z.
 * Every cell starts [CellState.UNKNOWN].
 */
class OccupancyGrid(val cellSize: Float, val halfSize: Float) {
    init {
        require(cellSize > 0f && halfSize > 0f) { "cellSize and halfSize must be positive" }
    }

    val size: Int = (2 * halfSize / cellSize).roundToInt()

    private val cells = Array(size * size) { CellState.UNKNOWN }

    operator fun contains(cell: Cell): Boolean =
        cell.col in 0 until size && cell.row in 0 until size

    operator fun get(cell: Cell): CellState {
        require(cell in this) { "$cell is outside the grid" }
        return cells[cell.row * size + cell.col]
    }

    operator fun set(cell: Cell, state: CellState) {
        require(cell in this) { "$cell is outside the grid" }
        cells[cell.row * size + cell.col] = state
    }

    /** The cell that contains [point], or `null` outside the grid. */
    fun cellAt(point: FloorPoint): Cell? {
        val cell = Cell(
            col = floor((point.x + halfSize) / cellSize).toInt(),
            row = floor((point.z + halfSize) / cellSize).toInt(),
        )
        return cell.takeIf { it in this }
    }

    fun centerOf(cell: Cell): FloorPoint = FloorPoint(
        x = (cell.col + 0.5f) * cellSize - halfSize,
        z = (cell.row + 0.5f) * cellSize - halfSize,
    )

    /** Sets every cell whose center lies inside the axis-aligned rectangle. */
    fun markRect(center: FloorPoint, halfWidth: Float, halfDepth: Float, state: CellState) {
        for (row in 0 until size) {
            for (col in 0 until size) {
                val cell = Cell(col, row)
                val c = centerOf(cell)
                if (abs(c.x - center.x) <= halfWidth && abs(c.z - center.z) <= halfDepth) {
                    this[cell] = state
                }
            }
        }
    }
}
