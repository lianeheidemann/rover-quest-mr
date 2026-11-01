package io.github.lianeheidemann.roverquest.ui

import android.util.Log
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Frame
import io.github.lianeheidemann.roverquest.core.depth.ColoredDepth
import io.github.lianeheidemann.roverquest.core.depth.DepthQuality
import io.github.lianeheidemann.roverquest.core.depth.DepthReport
import io.github.lianeheidemann.roverquest.core.depth.analyzeDepth
import io.github.lianeheidemann.roverquest.core.depth.colorize
import io.github.sceneview.ar.recording.ARRecorder
import io.github.sceneview.ar.recording.rememberARRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.lianeheidemann.roverquest.core.floor.FloorSetup
import io.github.lianeheidemann.roverquest.core.floor.PlacementRejection
import io.github.lianeheidemann.roverquest.core.floor.PlacementResult
import io.github.lianeheidemann.roverquest.core.floor.SetupHint
import io.github.lianeheidemann.roverquest.core.floor.SetupPhase
import io.github.lianeheidemann.roverquest.core.floor.setupHint
import io.github.lianeheidemann.roverquest.core.nav.RoutePlan
import io.github.lianeheidemann.roverquest.core.nav.planRoute
import io.github.lianeheidemann.roverquest.core.nav.samplePoints
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberOnGestureListener

private const val TAG = "RoverQuest"

private val FLOOR = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)

/** Distance between the dots that draw the route on the floor. */
private const val ROUTE_DOT_SPACING = 0.08f

/** What a tap does once the rover is on the floor. */
private enum class TapMode { DESTINATION, OBSTACLE }

/** Depth is read and drawn at most 10 times per second. */
private const val DEPTH_UPDATE_INTERVAL_NS = 100_000_000L

/** Without a new depth image for this long, the reading is reported as missing. */
private const val DEPTH_STALE_AFTER_NS = 1_500_000_000L

private fun depthAvailable(support: DepthSupport): Boolean = support != DepthSupport.NONE

/** The most recent session recording, or `null` when none was made yet. */
private fun newestRecording(dir: File): File? =
    dir.listFiles { file -> file.extension == "mp4" && file.length() > 0 }?.maxByOrNull { it.lastModified() }

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
    var rejection by remember { mutableStateOf<PlacementRejection?>(null) }
    var tapMode by remember { mutableStateOf(TapMode.DESTINATION) }

    // Depth diagnostics and session recording (Part 4).
    val context = LocalContext.current
    val recorder = rememberARRecorder()
    // App-private folder: no storage permission needed.
    val recordingsDir = remember {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "ar-recordings").apply { mkdirs() }
    }
    var lastRecording by remember { mutableStateOf(newestRecording(recordingsDir)) }
    var playbackFile by remember { mutableStateOf<File?>(null) }
    var sessionGeneration by remember { mutableIntStateOf(0) }
    var diagnosticOn by remember { mutableStateOf(false) }
    var depthReport by remember { mutableStateOf(DepthReport(DepthQuality.WAITING)) }
    var coloredDepth by remember { mutableStateOf<ColoredDepth?>(null) }
    var lastDepthAtNs by remember { mutableLongStateOf(0L) }

    val route = remember(setup) { planRoute(setup) }
    val routeDots = remember(route) {
        (route as? RoutePlan.Route)?.let { samplePoints(it.points, ROUTE_DOT_SPACING) }.orEmpty()
    }

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
        val result = when (tapMode) {
            TapMode.DESTINATION -> setup.placeDestination(local[0], local[1], local[2])
            TapMode.OBSTACLE -> setup.placeObstacle(local[0], local[1], local[2])
        }
        when (result) {
            is PlacementResult.Accepted -> {
                setup = result.setup
                Log.i(TAG, "Placed $tapMode: destination=${setup.destination} obstacle=${setup.obstacle}")
            }
            is PlacementResult.Rejected -> rejection = result.reason
        }
    }

    fun restart() {
        floorAnchor?.detach()
        floorAnchor = null
        setup = setup.reset()
        rejection = null
        tapMode = TapMode.DESTINATION
    }

    fun clearDepth() {
        depthReport = analyzeDepth(depthAvailable(depthSupport), null)
        coloredDepth = null
        lastDepthAtNs = 0L
    }

    /** Reads depth at most every [DEPTH_UPDATE_INTERVAL_NS]; runs on the ARCore frame thread. */
    fun updateDepth(frame: Frame) {
        if (frame.timestamp - lastDepthAtNs < DEPTH_UPDATE_INTERVAL_NS) return
        val depthFrame = readDepthFrame(frame)
        if (depthFrame == null) {
            // No image this frame: keep the last one for a moment, then say depth is missing.
            if (frame.timestamp - lastDepthAtNs > DEPTH_STALE_AFTER_NS) {
                depthReport = analyzeDepth(depthAvailable(depthSupport), null)
                coloredDepth = null
            }
            return
        }
        lastDepthAtNs = frame.timestamp
        depthReport = analyzeDepth(depthAvailable(depthSupport), depthFrame)
        // The app is portrait-only: the sensor image is a quarter turn clockwise from the display.
        coloredDepth = colorize(depthFrame, quarterTurns = 1)
    }

    fun setDiagnostic(on: Boolean) {
        if (!on && recorder.state == ARRecorder.State.RECORDING) recorder.stop()
        diagnosticOn = on
        clearDepth()
    }

    fun toggleRecording() {
        if (recorder.state == ARRecorder.State.RECORDING) {
            recorder.stop()
            lastRecording = newestRecording(recordingsDir)
            Log.i(TAG, "Recording saved: $lastRecording")
        } else {
            val name = "session-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.mp4"
            recorder.start(File(recordingsDir, name))
        }
    }

    // A new AR session starts with no planes and no anchors, so everything tied to the old
    // session (floor, rover, destination, depth) starts over.
    fun forgetSession() {
        if (recorder.state == ARRecorder.State.RECORDING) recorder.stop()
        restart()
        floorDetected = false
        reticleHit = null
        trackingState = TrackingState.PAUSED
        failureReason = null
        clearDepth()
    }

    fun leavePlayback() {
        forgetSession()
        playbackFile = null
    }

    fun toggleReplay() {
        if (playbackFile != null) {
            leavePlayback()
        } else {
            val file = lastRecording ?: return
            forgetSession()
            playbackFile = file
        }
    }

    /** Drops every detected floor and starts scanning again, for when ARCore picked a bad area. */
    fun chooseNewArea() {
        forgetSession()
        sessionGeneration++
        Log.i(TAG, "Scanning a new area")
    }

    val hint = setupHint(
        setup = setup,
        cameraTracking = trackingState == TrackingState.TRACKING,
        anchorStopped = anchorState == TrackingState.STOPPED,
        floorDetected = floorDetected,
        aimingAtFloor = reticleHit != null,
        routeBlocked = route is RoutePlan.Blocked,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // A new session is needed to switch between live camera and a recorded session, and to
        // forget the detected floor ("Escolher outra área"): ARCore cannot drop planes otherwise.
        key(playbackFile, sessionGeneration) {
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
                playbackDataset = playbackFile,
                onPlaybackFailed = { exception ->
                    Log.e(TAG, "Playback failed", exception)
                    leavePlayback()
                },
                // Depth costs CPU, so it is only turned on for diagnostics (Part 4).
                // ARSceneView downgrades it to DISABLED where it is not supported.
                depthMode = if (diagnosticOn) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED,
                onSessionUpdated = { session, frame ->
                    recorder.recordFrame(session)
                    if (diagnosticOn) updateDepth(frame)
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
                val obstacleMark = remember(materialLoader) {
                    materialLoader.createColorInstance(Color(0xFFE53935).copy(alpha = 0.7f), roughness = 0.8f)
                }
                val routeMark = remember(materialLoader) {
                    materialLoader.createUnlitColorInstance(Color(0xFF29B6F6))
                }
                val reticleMark = remember(materialLoader) {
                    materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.8f))
                }

                // Always on: after both are placed, a tap moves the destination or the obstacle.
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
                        setup.obstacle?.let { obstacle ->
                            // Virtual stand-in for a real box until depth detection (Parts 4 and 5).
                            CubeNode(
                                size = Size(FloorSetup.OBSTACLE_SIZE, 0.20f, FloorSetup.OBSTACLE_SIZE),
                                center = Position(y = 0.10f),
                                position = Position(x = obstacle.x, z = obstacle.z),
                                materialInstance = obstacleMark,
                            )
                        }
                        for (dot in routeDots) {
                            key(dot) {
                                CylinderNode(
                                    radius = 0.015f,
                                    height = 0.003f,
                                    position = Position(x = dot.x, y = 0.002f, z = dot.z),
                                    materialInstance = routeMark,
                                )
                            }
                        }
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
        }

        if (diagnosticOn) {
            coloredDepth?.let { DepthOverlay(it) }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        ) {
            StatusPanel(
                trackingText = trackingLabel(trackingState, failureReason),
                depthText = depthLabel(depthSupport),
                diagnosticOn = diagnosticOn,
                onToggleDiagnostic = { setDiagnostic(!diagnosticOn) },
            )
            if (diagnosticOn) {
                DiagnosticPanel(
                    report = depthReport,
                    recording = recorder.state == ARRecorder.State.RECORDING,
                    recordingError = recorder.errorMessage.takeIf {
                        recorder.state == ARRecorder.State.ERROR || recorder.state == ARRecorder.State.IO_ERROR
                    },
                    playingBack = playbackFile != null,
                    canReplay = lastRecording != null,
                    onToggleRecording = ::toggleRecording,
                    onToggleReplay = ::toggleReplay,
                )
            }
        }

        SetupPanel(
            hintText = hintLabel(hint),
            warning = hint == SetupHint.TRACKING_LOST || hint == SetupHint.ANCHOR_LOST,
            rejectionText = rejection?.let(::rejectionLabel),
            tapMode = tapMode.takeIf { setup.roverPlaced },
            onTapModeChange = {
                tapMode = it
                rejection = null
            },
            hasObstacle = setup.obstacle != null,
            onRemoveObstacle = { setup = setup.withoutObstacle() },
            showRestart = setup.roverPlaced,
            onRestart = ::restart,
            showNewArea = playbackFile == null,
            onNewArea = ::chooseNewArea,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StatusPanel(
    trackingText: String,
    depthText: String,
    diagnosticOn: Boolean,
    onToggleDiagnostic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
    ) {
        Text(trackingText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(depthText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onToggleDiagnostic) {
            Text(
                if (diagnosticOn) "Ocultar diagnóstico de profundidade" else "Diagnóstico de profundidade",
                color = Color(0xFF81D4FA),
            )
        }
    }
}

@Composable
private fun SetupPanel(
    hintText: String,
    warning: Boolean,
    rejectionText: String?,
    tapMode: TapMode?,
    onTapModeChange: (TapMode) -> Unit,
    hasObstacle: Boolean,
    onRemoveObstacle: () -> Unit,
    showRestart: Boolean,
    onRestart: () -> Unit,
    showNewArea: Boolean,
    onNewArea: () -> Unit,
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
        if (tapMode != null) {
            Text(
                "Obstáculos reais ainda não são detectados: use o obstáculo virtual.",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tocar coloca:", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                TapModeChip("Destino", tapMode == TapMode.DESTINATION) { onTapModeChange(TapMode.DESTINATION) }
                TapModeChip("Obstáculo", tapMode == TapMode.OBSTACLE) { onTapModeChange(TapMode.OBSTACLE) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasObstacle) {
                Button(onClick = onRemoveObstacle) { Text("Tirar obstáculo") }
            }
            if (showRestart) {
                Button(onClick = onRestart) { Text("Recomeçar") }
            }
        }
        if (showNewArea) {
            TextButton(onClick = onNewArea) {
                Text("Escolher outra área", color = Color(0xFF81D4FA))
            }
        }
    }
}

@Composable
private fun TapModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
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
    SetupHint.NO_ROUTE -> "Não há caminho até o destino. Mova o obstáculo ou o destino."
    SetupHint.READY -> "Rota calculada. Ande ao redor: rover, destino e rota ficam presos ao piso."
}

private fun rejectionLabel(reason: PlacementRejection): String = when (reason) {
    PlacementRejection.NO_ROVER -> "Coloque o rover primeiro."
    PlacementRejection.NOT_SAME_FLOOR -> "Precisa ficar no mesmo piso do rover."
    PlacementRejection.OUT_OF_AREA -> "Muito longe do rover. Escolha um ponto mais perto."
    PlacementRejection.TOO_CLOSE -> "Escolha um destino mais longe do rover."
    PlacementRejection.BLOCKED_BY_OBSTACLE -> "O destino não pode ficar em cima do obstáculo."
    PlacementRejection.COVERS_ROVER -> "O obstáculo não pode ficar em cima do rover."
    PlacementRejection.COVERS_DESTINATION -> "O obstáculo não pode ficar em cima do destino."
}
