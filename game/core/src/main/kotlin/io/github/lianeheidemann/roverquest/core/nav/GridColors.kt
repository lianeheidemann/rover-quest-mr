package io.github.lianeheidemann.roverquest.core.nav

/** Debug colors (ARGB) for the occupancy overlay drawn on the floor. */
const val COLOR_FREE = 0x5543A047          // translucent green
const val COLOR_OCCUPIED = 0xCCE53935.toInt() // red: depth saw something here
const val COLOR_CLEARANCE = 0x88FB8C00.toInt() // orange: kept clear for the rover's size, or manual obstacle
const val COLOR_UNKNOWN = 0x00000000         // transparent: never observed

/**
 * One color per cell of [planning], row by row (row 0 first), for a debug overlay.
 * Cells occupied in the depth [map] are red; cells blocked only by inflation or the manual
 * obstacle are orange.
 */
fun gridColors(planning: OccupancyGrid, map: ObstacleMap?): IntArray {
    val out = IntArray(planning.size * planning.size)
    for (row in 0 until planning.size) {
        for (col in 0 until planning.size) {
            val cell = Cell(col, row)
            out[row * planning.size + col] = when (planning[cell]) {
                CellState.FREE -> COLOR_FREE
                CellState.UNKNOWN -> COLOR_UNKNOWN
                CellState.OCCUPIED ->
                    if (map?.stateOf(cell) == CellState.OCCUPIED) COLOR_OCCUPIED else COLOR_CLEARANCE
            }
        }
    }
    return out
}
