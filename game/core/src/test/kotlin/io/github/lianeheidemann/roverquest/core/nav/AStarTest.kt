package io.github.lianeheidemann.roverquest.core.nav

import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AStarTest {

    /** 10 × 10 grid of 1 m cells, all free. */
    private fun freeGrid() = OccupancyGrid(cellSize = 1f, halfSize = 5f).apply {
        markRect(FloorPoint(0f, 0f), 5f, 5f, CellState.FREE)
    }

    private fun assertValidPath(grid: OccupancyGrid, cells: List<Cell>, start: Cell, goal: Cell) {
        assertEquals(start, cells.first())
        assertEquals(goal, cells.last())
        for ((a, b) in cells.zipWithNext()) {
            assertTrue(kotlin.math.abs(a.col - b.col) <= 1 && kotlin.math.abs(a.row - b.row) <= 1, "$a -> $b")
        }
        assertTrue(cells.none { grid[it] == CellState.OCCUPIED })
    }

    @Test
    fun `grid maps points to cells and back`() {
        val grid = OccupancyGrid(cellSize = 0.05f, halfSize = 2.5f)
        assertEquals(100, grid.size)
        assertEquals(Cell(50, 50), grid.cellAt(FloorPoint(0.01f, 0.01f)))
        assertEquals(Cell(49, 50), grid.cellAt(FloorPoint(-0.01f, 0.01f)))
        assertEquals(null, grid.cellAt(FloorPoint(2.6f, 0f)))
        val center = grid.centerOf(Cell(50, 50))
        assertEquals(0.025f, center.x, 1e-5f)
        assertEquals(0.025f, center.z, 1e-5f)
    }

    @Test
    fun `cells start unknown`() {
        val grid = OccupancyGrid(cellSize = 1f, halfSize = 2f)
        assertEquals(CellState.UNKNOWN, grid[Cell(0, 0)])
    }

    @Test
    fun `straight path in a free grid`() {
        val grid = freeGrid()
        val result = findPath(grid, Cell(0, 5), Cell(9, 5))
        assertIs<PathResult.Found>(result)
        assertEquals(10, result.cells.size)
        assertValidPath(grid, result.cells, Cell(0, 5), Cell(9, 5))
    }

    @Test
    fun `diagonal path is shortest`() {
        val result = findPath(freeGrid(), Cell(0, 0), Cell(9, 9))
        assertIs<PathResult.Found>(result)
        assertEquals(10, result.cells.size)
    }

    @Test
    fun `path goes around a wall`() {
        val grid = freeGrid()
        for (row in 0..7) grid[Cell(5, row)] = CellState.OCCUPIED
        val result = findPath(grid, Cell(2, 2), Cell(8, 2))
        assertIs<PathResult.Found>(result)
        assertValidPath(grid, result.cells, Cell(2, 2), Cell(8, 2))
        assertTrue(result.cells.any { it.row >= 8 }, "must pass below the wall")
    }

    @Test
    fun `no corner cutting between diagonal obstacles`() {
        val grid = freeGrid()
        grid[Cell(1, 0)] = CellState.OCCUPIED
        grid[Cell(0, 1)] = CellState.OCCUPIED
        // The only way out of (0,0) would squeeze between two occupied cells.
        assertEquals(
            PathResult.NotFound(NoPathReason.UNREACHABLE),
            findPath(grid, Cell(0, 0), Cell(9, 9)),
        )
    }

    @Test
    fun `fully blocked goal is unreachable`() {
        val grid = freeGrid()
        for (col in 0..9) grid[Cell(col, 5)] = CellState.OCCUPIED
        assertEquals(
            PathResult.NotFound(NoPathReason.UNREACHABLE),
            findPath(grid, Cell(0, 0), Cell(0, 9)),
        )
    }

    @Test
    fun `occupied start or goal is reported`() {
        val grid = freeGrid()
        grid[Cell(0, 0)] = CellState.OCCUPIED
        grid[Cell(9, 9)] = CellState.OCCUPIED
        assertEquals(PathResult.NotFound(NoPathReason.START_BLOCKED), findPath(grid, Cell(0, 0), Cell(5, 5)))
        assertEquals(PathResult.NotFound(NoPathReason.GOAL_BLOCKED), findPath(grid, Cell(5, 5), Cell(9, 9)))
    }

    @Test
    fun `cells outside the grid are reported`() {
        assertEquals(
            PathResult.NotFound(NoPathReason.OUT_OF_GRID),
            findPath(freeGrid(), Cell(0, 0), Cell(10, 0)),
        )
    }

    @Test
    fun `unknown cells are crossed only when allowed`() {
        val grid = OccupancyGrid(cellSize = 1f, halfSize = 5f)
        assertEquals(
            PathResult.NotFound(NoPathReason.START_BLOCKED),
            findPath(grid, Cell(0, 0), Cell(9, 9)),
        )
        assertIs<PathResult.Found>(findPath(grid, Cell(0, 0), Cell(9, 9), allowUnknown = true))
    }

    @Test
    fun `start equal to goal is a one-cell path`() {
        assertEquals(PathResult.Found(listOf(Cell(3, 3))), findPath(freeGrid(), Cell(3, 3), Cell(3, 3)))
    }
}
