package io.github.lianeheidemann.roverquest.ui.ar

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.floor.SetupHint
import io.github.lianeheidemann.roverquest.core.floor.SetupPhase
import io.github.lianeheidemann.roverquest.core.floor.setupHint
import io.github.lianeheidemann.roverquest.core.nav.MotionStatus
import io.github.lianeheidemann.roverquest.core.nav.RoutePlan
import io.github.lianeheidemann.roverquest.core.nav.buildGrid
import io.github.lianeheidemann.roverquest.core.nav.gridColors
import io.github.lianeheidemann.roverquest.core.nav.planRoute
import io.github.lianeheidemann.roverquest.core.nav.samplePoints
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import io.github.lianeheidemann.roverquest.ui.depthLabel
import io.github.lianeheidemann.roverquest.ui.hintLabel
import io.github.lianeheidemann.roverquest.ui.panels.DepthOverlay
import io.github.lianeheidemann.roverquest.ui.panels.DiagnosticPanel
import io.github.lianeheidemann.roverquest.ui.panels.SetupPanel
import io.github.lianeheidemann.roverquest.ui.panels.StatusBar
import io.github.lianeheidemann.roverquest.ui.rejectionLabel
import io.github.lianeheidemann.roverquest.ui.trackingLabel
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.rememberOnGestureListener
import java.util.Locale
import kotlin.math.hypot

/** Distance between the dots that draw the route on the floor. */
private const val ROUTE_DOT_SPACING = 0.08f

/**
 * The game screen: the AR session, the 3D scene and the panels. It wires the state holders
 * ([GameState], [DepthMapper], [SessionRecordings]) to the scene and the panels.
 */
@Composable
fun ArScreen(onSessionFailed: (String) -> Unit) {
    val game = rememberGameState()
    val depth = rememberDepthMapper()
    val recordings = rememberSessionRecordings()

    // What ARCore reports about the session.
    var trackingState by remember { mutableStateOf(TrackingState.PAUSED) }
    var failureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var floorDetected by remember { mutableStateOf(false) }
    var reticleHit by remember { mutableStateOf<HitResult?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var sessionGeneration by remember { mutableIntStateOf(0) }

    // Screen options.
    var diagnosticOn by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    // Phone torch, for playing in dim rooms: more light also helps tracking and depth.
    var torchOn by remember { mutableStateOf(false) }

    val setup = game.setup
    val motion = game.motion
    val map = depth.obstacleMap.takeIf { depth.mapping }
    val route = remember(setup, depth.mapVersion, depth.mapping, game.routeStart) {
        planRoute(setup, map, rover = game.routeStart)
    }
    val overlay = remember(setup, depth.overlayVersion, depth.mapping, showMap) {
        if (!showMap) null
        else MapOverlayData(gridColors(buildGrid(setup, map, game.routeStart), map), depth.obstacleMap.size, depth.overlayVersion)
    }
    val roverPose = game.roverPose(route)
    // No dots once arrived: the rover stops just short of the flag, with nothing left to drive.
    val arrived = motion.status == MotionStatus.ARRIVED
    val routeDots = remember(route, arrived) {
        if (arrived) emptyList()
        else (route as? RoutePlan.Route)?.let { samplePoints(it.points, ROUTE_DOT_SPACING) }.orEmpty()
    }
    // Dots the rover already drove over are hidden; dot i sits (i + 1) spacings along the route.
    val hiddenDots = if (motion.isRunning) (motion.progress / ROUTE_DOT_SPACING).toInt() else 0

    // Keep planning from the rover, and follow replanned routes while driving.
    LaunchedEffect(motion.status) { game.onMotionStatusChanged() }
    LaunchedEffect(setup, depth.mapVersion) { game.onPlanInputsChanged() }
    LaunchedEffect(route) { game.onRouteChanged(route) }

    val tracking = trackingState == TrackingState.TRACKING && game.anchorState == TrackingState.TRACKING
    RunEachFrame(motion.isRunning) { seconds -> game.stepMotion(seconds, tracking, route is RoutePlan.Route) }
    RunEachFrame(game.greeting != null) { seconds -> game.stepGreeting(seconds, tracking) }
    RunEachFrame(game.dance != null) { seconds -> game.stepDance(seconds, tracking) }

    fun restart() {
        game.restart()
        depth.resetMap()
    }

    fun setDiagnostic(on: Boolean) {
        if (!on) recordings.stop()
        diagnosticOn = on
        depth.clearReport()
    }

    // A new AR session starts with no planes and no anchors, so everything tied to the old
    // session (floor, rover, destination, depth) starts over.
    fun forgetSession() {
        recordings.stop()
        game.newGame()
        depth.resetMap()
        floorDetected = false
        reticleHit = null
        trackingState = TrackingState.PAUSED
        failureReason = null
        depth.clearReport()
    }

    fun leavePlayback() {
        forgetSession()
        recordings.playback = null
    }

    fun toggleReplay() {
        if (recordings.playback != null) {
            leavePlayback()
        } else {
            val file = recordings.last ?: return
            forgetSession()
            recordings.playback = file
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
        anchorStopped = game.anchorState == TrackingState.STOPPED,
        floorDetected = floorDetected,
        aimingAtFloor = reticleHit != null,
        routeBlocked = route is RoutePlan.Blocked,
        routeNeedsScan = route is RoutePlan.NeedsScan,
        driving = motion.isRunning,
        arrived = arrived,
        greeting = game.greeting != null,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // A new session is needed to switch between live camera and a recorded session, and to
        // forget the detected floor ("Resetar jogo"): ARCore cannot drop planes otherwise.
        key(recordings.playback, sessionGeneration) {
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
                    depth.support = support
                    Log.i(TAG, "Depth support: $support")
                },
                playbackDataset = recordings.playback,
                onPlaybackFailed = { exception ->
                    Log.e(TAG, "Playback failed", exception)
                    leavePlayback()
                },
                // Depth feeds the obstacle map (Part 5) and the diagnostics (Part 4).
                // ARSceneView downgrades it to DISABLED where it is not supported.
                depthMode = Config.DepthMode.AUTOMATIC,
                // ARCore silently keeps the torch off on devices that cannot drive it.
                flashMode = if (torchOn) Config.FlashMode.TORCH else Config.FlashMode.OFF,
                onSessionUpdated = { session, frame ->
                    recordings.record(session)
                    frame.camera.pose.getTranslation(game.cameraPosition, 0)
                    val anchor = game.floorAnchor
                    if (diagnosticOn || anchor != null) depth.update(frame, anchor, diagnosticOn, showMap)
                    val state = frame.camera.trackingState
                    if (state != trackingState) trackingState = state
                    // Live, not latched: in a dark room ARCore can see the floor for a moment and
                    // lose it again, and the player must not be asked to aim at a floor that is gone.
                    val floorNow = session.getAllTrackables(Plane::class.java).any {
                        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            it.trackingState == TrackingState.TRACKING
                    }
                    if (floorNow != floorDetected) floorDetected = floorNow
                },
                onTrackingFailureChanged = { failureReason = it },
                onSessionFailed = { exception ->
                    Log.e(TAG, "AR session failed", exception)
                    onSessionFailed(exception.message ?: exception::class.java.simpleName)
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { _, _ ->
                        game.onTap(reticleHit, cameraTracking = trackingState == TrackingState.TRACKING)
                    },
                ),
            ) {
                ArSceneContent(
                    viewSize = viewSize,
                    onReticleHit = { reticleHit = it },
                    anchor = game.floorAnchor,
                    onAnchorState = { game.anchorState = it },
                    roverPose = roverPose,
                    roverLift = game.dance?.lift ?: 0f,
                    setup = setup,
                    routeDots = routeDots,
                    hiddenDots = hiddenDots,
                    overlay = overlay,
                    modelGeneration = game.modelGeneration,
                )
            }
        }

        if (diagnosticOn) {
            depth.colored?.let { DepthOverlay(it) }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusBar(
                tracking = trackingState == TrackingState.TRACKING,
                trackingText = trackingLabel(trackingState, failureReason),
                // Before the area is accepted only the torch is offered: it helps find the floor.
                showGameTools = setup.areaConfirmed,
                diagnosticOn = diagnosticOn,
                onToggleDiagnostic = { setDiagnostic(!diagnosticOn) },
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                showMap = showMap,
                onToggleMap = {
                    showMap = !showMap
                    depth.refreshOverlay()
                },
                canChooseNewArea = recordings.playback == null,
                onNewArea = ::chooseNewArea,
            )
            if (diagnosticOn && setup.areaConfirmed) {
                DiagnosticPanel(
                    report = depth.report,
                    // Floor estimate from depth, to see on the phone whether it stays steady.
                    supportText = depthLabel(depth.support) + if (game.floorAnchor != null && depth.mapping) {
                        val floor = depth.floorLevel
                        val tilt = hypot(floor.b, floor.c) * 100
                        String.format(Locale("pt", "BR"), " · chão %+.1f cm · incl. %.1f%%", floor.a * 100, tilt)
                    } else "",
                    recording = recordings.recording,
                    recordingError = recordings.error,
                    playingBack = recordings.playback != null,
                    canReplay = recordings.last != null,
                    onToggleRecording = recordings::toggle,
                    onToggleReplay = ::toggleReplay,
                )
            }
        }

        SetupPanel(
            hintText = hintLabel(hint, (route as? RoutePlan.Blocked)?.reason),
            warning = hint == SetupHint.TRACKING_LOST || hint == SetupHint.ANCHOR_LOST,
            rejectionText = game.rejection?.let(::rejectionLabel),
            noteText = "Sem profundidade: só o obstáculo virtual bloqueia."
                .takeIf { setup.roverPlaced && depth.support == DepthSupport.NONE },
            searching = hint == SetupHint.SCAN_FLOOR,
            confirmingArea = hint == SetupHint.CONFIRM_AREA,
            // Only an area the circle is on can be accepted.
            canConfirm = reticleHit != null,
            // Until the rover is placed there is always a way back to scanning.
            showNewArea = setup.phase == SetupPhase.PLACE_ROVER && recordings.playback == null,
            onConfirmArea = game::confirmArea,
            onNewArea = ::chooseNewArea,
            tapMode = game.tapMode.takeIf { setup.roverPlaced && game.greeting == null },
            onTapModeChange = game::selectTapMode,
            hasObstacle = setup.obstacle != null,
            onRemoveObstacle = game::removeObstacle,
            showRestart = setup.roverPlaced && game.greeting == null,
            onRestart = ::restart,
            canGo = setup.phase == SetupPhase.READY && route is RoutePlan.Route &&
                !motion.isRunning && !arrived,
            driving = motion.isRunning,
            onGo = { game.go(route) },
            onStop = game::stop,
            // The area question is the one decision on screen: centered, so it is not missed.
            modifier = Modifier.align(if (hint == SetupHint.CONFIRM_AREA) Alignment.Center else Alignment.BottomCenter),
        )
    }
}
