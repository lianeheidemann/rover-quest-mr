package io.github.lianeheidemann.roverquest.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.floor.DestinationRejection
import io.github.lianeheidemann.roverquest.core.floor.DestinationResult
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.SetupHint
import io.github.lianeheidemann.roverquest.core.floor.SetupPhase
import io.github.lianeheidemann.roverquest.core.floor.setupHint
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberOnGestureListener

private const val TAG = "RoverQuest"

private val FLOOR = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)

@Composable
fun ArScreen(onSessionFailed: (String) -> Unit) {
    var trackingState by remember { mutableStateOf(TrackingState.PAUSED) }
    var failureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var depthSupport by remember { mutableStateOf(DepthSupport.UNKNOWN) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var floorDetected by remember { mutableStateOf(false) }
    var reticleHit by remember { mutableStateOf<HitResult?>(null) }
    var floorAnchor by remember { mutableStateOf<Anchor?>(null) }
    var anchorState by remember { mutableStateOf(TrackingState.TRACKING) }
    var setup by remember { mutableStateOf(FloorSetup()) }
    var rejection by remember { mutableStateOf<DestinationRejection?>(null) }

    fun onTap() {
        val hit = reticleHit ?: return
        if (trackingState != TrackingState.TRACKING) return
        rejection = null
        val anchor = floorAnchor
        if (anchor == null) {
            val created = hit.createAnchorOrNull() ?: return
            floorAnchor = created
            anchorState = TrackingState.TRACKING
            setup = setup.withRover()
            Log.i(TAG, "Rover placed")
            return
        }
        val local = anchor.pose.inverse().transformPoint(hit.hitPose.translation)
        when (val result = setup.placeDestination(local[0], local[1], local[2])) {
            is DestinationResult.Accepted -> {
                setup = result.setup
                Log.i(TAG, "Destination placed at ${result.setup.destination}")
            }
            is DestinationResult.Rejected -> rejection = result.reason
        }
    }

    fun restart() {
        floorAnchor?.detach()
        floorAnchor = null
        setup = setup.reset()
        rejection = null
    }

    val hint = setupHint(
        setup = setup,
        cameraTracking = trackingState == TrackingState.TRACKING,
        anchorStopped = anchorState == TrackingState.STOPPED,
        floorDetected = floorDetected,
        aimingAtFloor = reticleHit != null,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it },
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            // Floor dots show where the rover can go; hide them once both are placed.
            planeRenderer = setup.phase != SetupPhase.READY,
            onSessionCreated = { session ->
                val support = DepthSupport.from(
                    automatic = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC),
                    raw = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY),
                )
                depthSupport = support
                Log.i(TAG, "Depth support: $support")
            },
            onSessionUpdated = { session, frame ->
                val state = frame.camera.trackingState
                if (state != trackingState) trackingState = state
                if (!floorDetected) {
                    floorDetected = session.getAllTrackables(Plane::class.java).any {
                        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            it.trackingState == TrackingState.TRACKING
                    }
                }
            },
            onTrackingFailureChanged = { failureReason = it },
            onSessionFailed = { exception ->
                Log.e(TAG, "AR session failed", exception)
                onSessionFailed(exception.message ?: exception::class.java.simpleName)
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, _ -> onTap() },
            ),
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
            val reticleMark = remember(materialLoader) {
                materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.8f))
            }

            // Always on: after both are placed, a tap moves the destination.
            if (viewSize != IntSize.Zero) {
                ReticleNode(
                    xPx = viewSize.width / 2f,
                    yPx = viewSize.height / 2f,
                    planeTypes = FLOOR,
                    onHitResultChanged = { reticleHit = it },
                ) {
                    CylinderNode(radius = 0.05f, height = 0.002f, materialInstance = reticleMark)
                }
            }

            floorAnchor?.let { anchor ->
                AnchorNode(
                    anchor = anchor,
                    onTrackingStateChanged = { anchorState = it },
                ) {
                    // Simple rover at the anchor origin; the sphere marks its front (-Z).
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
                    setup.destination?.let { destination ->
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

        StatusPanel(
            trackingText = trackingLabel(trackingState, failureReason),
            depthText = depthLabel(depthSupport),
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SetupPanel(
            hintText = hintLabel(hint),
            warning = hint == SetupHint.TRACKING_LOST || hint == SetupHint.ANCHOR_LOST,
            rejectionText = rejection?.let(::rejectionLabel),
            showRestart = setup.roverPlaced,
            onRestart = ::restart,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StatusPanel(trackingText: String, depthText: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(trackingText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(depthText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupPanel(
    hintText: String,
    warning: Boolean,
    rejectionText: String?,
    showRestart: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (warning) Color(0xFFB71C1C) else Color.Black
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .background(background.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            hintText,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (rejectionText != null) {
            Text(
                rejectionText,
                color = Color(0xFFFFD54F),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showRestart) {
            Button(onClick = onRestart) { Text("Recomeçar") }
        }
    }
}

private fun trackingLabel(state: TrackingState, reason: TrackingFailureReason?): String = when (state) {
    TrackingState.TRACKING -> "Rastreamento: ativo"
    TrackingState.STOPPED -> "Rastreamento: parado"
    TrackingState.PAUSED -> when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Rastreamento: pouca luz"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Rastreamento: mova o celular mais devagar"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Rastreamento: aponte para uma área com mais detalhes"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Rastreamento: câmera indisponível"
        else -> "Rastreamento: mova o celular devagar para começar"
    }
}

private fun depthLabel(support: DepthSupport): String = when (support) {
    DepthSupport.UNKNOWN -> "Profundidade: verificando…"
    DepthSupport.NONE -> "Profundidade: não suportada"
    DepthSupport.AUTOMATIC_ONLY -> "Profundidade: suportada (sem Raw Depth)"
    DepthSupport.AUTOMATIC_AND_RAW -> "Profundidade: suportada (com Raw Depth)"
}

private fun hintLabel(hint: SetupHint): String = when (hint) {
    SetupHint.TRACKING_LOST -> "Rastreamento perdido. Aponte de novo para o piso, devagar."
    SetupHint.ANCHOR_LOST -> "O rover se perdeu no piso. Toque em Recomeçar."
    SetupHint.SCAN_FLOOR -> "Mova o celular devagar, apontando para o piso, para encontrá-lo."
    SetupHint.AIM_AT_FLOOR -> "Mire o círculo em uma área pontilhada do piso."
    SetupHint.TAP_TO_PLACE_ROVER -> "Toque para colocar o rover no círculo."
    SetupHint.TAP_TO_PLACE_DESTINATION -> "Toque para marcar o destino no círculo."
    SetupHint.READY -> "Rover e destino no piso. Ande ao redor ou toque para mover o destino."
}

private fun rejectionLabel(reason: DestinationRejection): String = when (reason) {
    DestinationRejection.NO_ROVER -> "Coloque o rover primeiro."
    DestinationRejection.NOT_SAME_FLOOR -> "O destino precisa ficar no mesmo piso do rover."
    DestinationRejection.TOO_CLOSE -> "Escolha um destino mais longe do rover."
}
