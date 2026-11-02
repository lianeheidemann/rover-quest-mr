package io.github.lianeheidemann.roverquest.ui.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.nav.ROUTE_CELL_SIZE
import io.github.lianeheidemann.roverquest.core.nav.RoverPose
import io.github.sceneview.ar.ARSceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberModelInstance

private val FLOOR = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)

/** Lightweight rover model; generated from `rover-mini.glb` (see game/README.md). */
private const val ROVER_MODEL_ASSET = "models/rover-mini-lite.glb"

/** The model's largest side, in meters; its footprint then fits the rover's 10 cm radius. */
private const val ROVER_MODEL_SIZE = 0.2f

/** Turn around the vertical axis so the model's front (its +X side, seen on the phone) faces -Z. */
private const val ROVER_MODEL_YAW = 90f

/** Destination flag; generated from `destination-flag.glb` (see game/README.md). */
private const val DESTINATION_MODEL_ASSET = "models/destination-flag-lite.glb"

/** Height of the destination flag, in meters. */
private const val DESTINATION_MODEL_SIZE = 0.3f

/** The debug map overlay: cell colors and the version they were built for. */
internal class MapOverlayData(val colors: IntArray, val gridSize: Int, val version: Int)

/**
 * The 3D scene: the placement reticle and, on the floor anchor, the rover, the destination flag,
 * the manual obstacle, the route dots and the debug map. Only draws; the state comes from outside.
 *
 * @param roverLift how high the rover is off the floor (its victory hops), in meters.
 * @param hiddenDots dots the rover already drove over, from the start of [routeDots].
 * @param modelGeneration bumped when the rover is removed: SceneView destroys a model's entities
 *   together with its node, so a reused instance would come back invisible. A new one is loaded as
 *   soon as the old rover leaves, before the next tap, so the stand-in shapes are not seen.
 */
@Composable
internal fun ARSceneScope.ArSceneContent(
    viewSize: IntSize,
    onReticleHit: (HitResult?) -> Unit,
    anchor: Anchor?,
    onAnchorState: (TrackingState) -> Unit,
    roverPose: RoverPose,
    roverLift: Float,
    setup: FloorSetup,
    routeDots: List<FloorPoint>,
    hiddenDots: Int,
    overlay: MapOverlayData?,
    modelGeneration: Int,
) {
    val roverBody = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFF5A623), roughness = 0.5f)
    }
    val roverEye = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF263238), roughness = 0.3f)
    }
    val destinationMark = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF00C853), roughness = 0.6f)
    }
    val obstacleMark = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFE53935).copy(alpha = 0.7f), roughness = 0.8f)
    }
    val routeMark = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(Color(0xFF29B6F6))
    }
    val reticleMark = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.8f))
    }
    val roverModel = key(modelGeneration) { rememberModelInstance(modelLoader, ROVER_MODEL_ASSET) }
    val destinationModel = key(modelGeneration) { rememberModelInstance(modelLoader, DESTINATION_MODEL_ASSET) }

    // Always on: after both are placed, a tap moves the destination or the obstacle.
    if (viewSize != IntSize.Zero) {
        ReticleNode(
            xPx = viewSize.width / 2f,
            yPx = viewSize.height / 2f,
            planeTypes = FLOOR,
            onHitResultChanged = onReticleHit,
        ) {
            CylinderNode(radius = 0.05f, height = 0.002f, materialInstance = reticleMark)
        }
    }

    anchor ?: return
    AnchorNode(anchor = anchor, onTrackingStateChanged = onAnchorState) {
        val model = roverModel
        // The rover turns to face where it will go: the route, or the destination.
        Node(
            position = Position(x = roverPose.position.x, y = roverLift, z = roverPose.position.z),
            rotation = Rotation(y = roverPose.heading),
        ) {
            if (model != null) {
                // Standing on the floor at the anchor origin, facing -Z.
                ModelNode(
                    modelInstance = model,
                    scaleToUnits = ROVER_MODEL_SIZE,
                    centerOrigin = Position(0f, -1f, 0f),
                    rotation = Rotation(y = ROVER_MODEL_YAW),
                )
            } else {
                // Simple rover while the model loads (or if it fails); the sphere marks its front (-Z).
                CubeNode(
                    size = Size(0.12f, 0.06f, 0.16f),
                    center = Position(y = 0.03f),
                    materialInstance = roverBody,
                )
                SphereNode(
                    radius = 0.02f,
                    position = Position(y = 0.05f, z = -0.07f),
                    materialInstance = roverEye,
                )
            }
        }
        setup.obstacle?.let { obstacle ->
            // Virtual stand-in for a real box, for testing without depth.
            CubeNode(
                size = Size(FloorSetup.OBSTACLE_SIZE, 0.20f, FloorSetup.OBSTACLE_SIZE),
                center = Position(y = 0.10f),
                position = Position(x = obstacle.x, z = obstacle.z),
                materialInstance = obstacleMark,
            )
        }
        overlay?.let {
            MapOverlay(
                colors = it.colors,
                gridSize = it.gridSize,
                cellSize = ROUTE_CELL_SIZE,
                halfSize = FloorSetup.PLAY_AREA_HALF_SIZE,
                version = it.version,
            )
        }
        routeDots.forEachIndexed { index, dot ->
            if (index >= hiddenDots) key(dot) {
                CylinderNode(
                    radius = 0.015f,
                    height = 0.003f,
                    position = Position(x = dot.x, y = 0.002f, z = dot.z),
                    materialInstance = routeMark,
                )
            }
        }
        setup.destination?.let { destination ->
            val flag = destinationModel
            if (flag != null) {
                // Standing on the floor at the destination.
                ModelNode(
                    modelInstance = flag,
                    scaleToUnits = DESTINATION_MODEL_SIZE,
                    centerOrigin = Position(0f, -1f, 0f),
                    position = Position(x = destination.x, z = destination.z),
                )
            } else {
                // Simple marker while the model loads (or if it fails).
                CylinderNode(
                    radius = 0.08f,
                    height = 0.004f,
                    position = Position(x = destination.x, y = 0.002f, z = destination.z),
                    materialInstance = destinationMark,
                )
                CylinderNode(
                    radius = 0.006f,
                    height = 0.25f,
                    position = Position(x = destination.x, y = 0.125f, z = destination.z),
                    materialInstance = destinationMark,
                )
            }
        }
    }
}
