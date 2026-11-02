package io.github.lianeheidemann.roverquest.ui.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.google.android.filament.RenderableManager
import io.github.lianeheidemann.roverquest.core.nav.COLOR_CLEARANCE
import io.github.lianeheidemann.roverquest.core.nav.COLOR_FREE
import io.github.lianeheidemann.roverquest.core.nav.COLOR_OCCUPIED
import io.github.sceneview.SceneScope
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.safeDestroyGeometry

/** Height of the debug grid above the floor, under the route dots. */
private const val OVERLAY_HEIGHT = 0.001f

/**
 * Debug view of the planning grid, drawn flat on the floor in the floor anchor's frame:
 * green = seen free, red = obstacle seen by depth, orange = kept clear for the rover (or the
 * manual obstacle). Unknown cells are not drawn.
 *
 * @param colors one ARGB color per cell, row by row, as produced by `gridColors`.
 * @param version changes whenever [colors] change, to rebuild the meshes.
 */
@Composable
internal fun SceneScope.MapOverlay(colors: IntArray, gridSize: Int, cellSize: Float, halfSize: Float, version: Int) {
    for (argb in intArrayOf(COLOR_FREE, COLOR_CLEARANCE, COLOR_OCCUPIED)) {
        val material = remember(materialLoader, argb) { materialLoader.createColorInstance(Color(argb)) }
        // A new geometry per update: the number of cells of each color changes every time.
        key(argb, version) {
            val vertices = remember { cellQuads(colors, argb, gridSize, cellSize, halfSize) }
            if (vertices.isNotEmpty()) {
                val geometry = remember {
                    Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
                        .vertices(vertices)
                        .indices(quadIndices(vertices.size / 4))
                        .build(engine)
                }
                DisposableEffect(geometry) { onDispose { engine.safeDestroyGeometry(geometry) } }
                MeshNode(
                    primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
                    vertexBuffer = geometry.vertexBuffer,
                    indexBuffer = geometry.indexBuffer,
                    boundingBox = geometry.boundingBox,
                    materialInstance = material,
                )
            }
        }
    }
}

private val UP = Direction(y = 1f)

/** Four corners per cell of the given color, at the floor, facing up. */
private fun cellQuads(colors: IntArray, argb: Int, gridSize: Int, cellSize: Float, halfSize: Float): List<Geometry.Vertex> {
    val vertices = ArrayList<Geometry.Vertex>()
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            if (colors[row * gridSize + col] != argb) continue
            val x0 = col * cellSize - halfSize
            val z0 = row * cellSize - halfSize
            val x1 = x0 + cellSize
            val z1 = z0 + cellSize
            vertices += Geometry.Vertex(position = Position(x0, OVERLAY_HEIGHT, z0), normal = UP)
            vertices += Geometry.Vertex(position = Position(x1, OVERLAY_HEIGHT, z0), normal = UP)
            vertices += Geometry.Vertex(position = Position(x1, OVERLAY_HEIGHT, z1), normal = UP)
            vertices += Geometry.Vertex(position = Position(x0, OVERLAY_HEIGHT, z1), normal = UP)
        }
    }
    return vertices
}

/** Two counter-clockwise triangles (seen from above) per quad. */
private fun quadIndices(quads: Int): List<Int> {
    val indices = ArrayList<Int>(quads * 6)
    for (q in 0 until quads) {
        val b = q * 4
        indices += listOf(b, b + 3, b + 2, b, b + 2, b + 1)
    }
    return indices
}
