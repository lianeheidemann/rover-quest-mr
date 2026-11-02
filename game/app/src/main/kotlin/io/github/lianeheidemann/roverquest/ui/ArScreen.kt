package io.github.lianeheidemann.roverquest.ui

import android.util.Log
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import io.github.lianeheidemann.roverquest.core.floor.FloorPoint
import io.github.lianeheidemann.roverquest.core.nav.MotionStatus
import io.github.lianeheidemann.roverquest.core.nav.RoverMotion
import io.github.lianeheidemann.roverquest.core.nav.RoverGreeting
import io.github.lianeheidemann.roverquest.core.nav.RoverDance
import io.github.lianeheidemann.roverquest.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Frame
import io.github.lianeheidemann.roverquest.core.depth.CameraIntrinsics
import io.github.lianeheidemann.roverquest.core.depth.ColoredDepth
import io.github.lianeheidemann.roverquest.core.depth.DepthFrame
import io.github.lianeheidemann.roverquest.core.depth.projectToFloor
import io.github.lianeheidemann.roverquest.core.depth.FloorLevel
import io.github.lianeheidemann.roverquest.core.nav.NoPathReason
import io.github.lianeheidemann.roverquest.core.nav.ROUTE_CELL_SIZE
import io.github.lianeheidemann.roverquest.core.nav.buildGrid
import io.github.lianeheidemann.roverquest.core.nav.gridColors
import io.github.lianeheidemann.roverquest.core.nav.newObstacleMap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.lianeheidemann.roverquest.core.floor.roverHeading
import io.github.lianeheidemann.roverquest.core.floor.headingDegrees
import io.github.lianeheidemann.roverquest.core.floor.setupHint
import io.github.lianeheidemann.roverquest.core.nav.RoutePlan
import io.github.lianeheidemann.roverquest.core.nav.planRoute
import io.github.lianeheidemann.roverquest.core.nav.samplePoints
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberOnGestureListener

private const val TAG = "RoverQuest"

private val FLOOR = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)

/** Distance between the dots that draw the route on the floor. */
private const val ROUTE_DOT_SPACING = 0.08f

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

/** What a tap does once the rover is on the floor. */
private enum class TapMode { DESTINATION, OBSTACLE }

/** Depth is read and drawn at most 10 times per second. */
private const val DEPTH_UPDATE_INTERVAL_NS = 100_000_000L

/** Without a new depth image for this long, the reading is reported as missing. */
private const val DEPTH_STALE_AFTER_NS = 1_500_000_000L

private fun depthAvailable(support: DepthSupport): Boolean = support != DepthSupport.NONE

/** The debug map overlay is rebuilt at most once per second. */
private const val OVERLAY_UPDATE_INTERVAL_NS = 1_000_000_000L

/** ARCore gives intrinsics for the full CPU image; scale them to the depth image. */
private fun depthIntrinsics(frame: Frame, depth: DepthFrame): CameraIntrinsics? {
    val intrinsics = frame.camera.imageIntrinsics
    val (width, height) = intrinsics.imageDimensions.let { it[0] to it[1] }
    val fx = intrinsics.focalLength[0]
    val fy = intrinsics.focalLength[1]
    if (width <= 0 || height <= 0 || fx <= 0f || fy <= 0f) return null
    val sx = depth.width / width.toFloat()
    val sy = depth.height / height.toFloat()
    return CameraIntrinsics(
        fx = fx * sx,
        fy = fy * sy,
        cx = intrinsics.principalPoint[0] * sx,
        cy = intrinsics.principalPoint[1] * sy,
    )
}

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

    // Obstacle map from depth (Part 5), in the floor anchor's frame. Without depth support the
    // route falls back to the manual obstacle only.
    val obstacleMap = remember { newObstacleMap() }
    // Heights are measured from the floor depth sees, not from the anchor (see FloorLevel).
    val floorLevel = remember { FloorLevel() }
    var mapVersion by remember { mutableIntStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    // Phone torch, for playing in dim rooms: more light also helps tracking and depth.
    var torchOn by remember { mutableStateOf(false) }
    val mapping = depthSupport == DepthSupport.AUTOMATIC_ONLY || depthSupport == DepthSupport.AUTOMATIC_AND_RAW

    // Rover driving along the route (Part 6). Routes are planned from where the rover is: its
    // pose when idle, and the pose at the last replan while driving.
    var motion by remember { mutableStateOf(RoverMotion()) }
    var routeStart by remember { mutableStateOf(FloorPoint.ORIGIN) }
    // When first placed, the rover drives up to the player to say hello, then goes back.
    var greeting by remember { mutableStateOf<RoverGreeting?>(null) }
    var greeted by remember { mutableStateOf(false) }
    // Victory dance when the rover reaches the destination.
    var dance by remember { mutableStateOf<RoverDance?>(null) }
    // Bumped when the rover is removed, to load a fresh model for the next one (see roverModel).
    var roverModelGeneration by remember { mutableIntStateOf(0) }
    // Latest camera position in world space; read when the rover is placed.
    val cameraPosition = remember { FloatArray(3) }
    val route = remember(setup, mapVersion, mapping, routeStart) {
        planRoute(setup, obstacleMap.takeIf { mapping }, rover = routeStart)
    }
    // The debug overlay is rebuilt at most once per second: it is many small quads.
    var overlayVersion by remember { mutableIntStateOf(0) }
    var lastOverlayAtNs by remember { mutableLongStateOf(0L) }
    val overlayColors = remember(setup, overlayVersion, mapping, showMap) {
        if (showMap) gridColors(buildGrid(setup, obstacleMap.takeIf { mapping }, routeStart), obstacleMap.takeIf { mapping }) else null
    }
    // Idle, the rover faces where it will go; once started, its own pose drives it.
    val idleHeading = remember(setup, route, motion.pose.position) {
        roverHeading(setup, (route as? RoutePlan.Route)?.points, from = motion.pose.position)
    }
    val roverPose = greeting?.pose
        ?: dance?.let { motion.pose.copy(heading = it.heading) }
        ?: if (motion.status == MotionStatus.IDLE) motion.pose.copy(heading = idleHeading) else motion.pose
    // No dots once arrived: the rover stops just short of the marker, with nothing left to drive.
    val arrived = motion.status == MotionStatus.ARRIVED
    val routeDots = remember(route, arrived) {
        if (arrived) emptyList()
        else (route as? RoutePlan.Route)?.let { samplePoints(it.points, ROUTE_DOT_SPACING) }.orEmpty()
    }
    // Dots the rover already drove over are hidden; dot i sits (i + 1) spacings along the route.
    val hiddenDots = if (motion.isRunning) (motion.progress / ROUTE_DOT_SPACING).toInt() else 0

    // Keep planning from the rover: from its pose while it waits, and from where it is whenever
    // the setup or the map changes while it drives (replanning).
    LaunchedEffect(motion.status) {
        if (!motion.isRunning) routeStart = motion.pose.position
    }
    LaunchedEffect(setup, mapVersion) {
        if (motion.isRunning) routeStart = motion.pose.position
    }
    LaunchedEffect(route) {
        val points = (route as? RoutePlan.Route)?.points
        if (motion.isRunning && points != null) motion = motion.withRoute(points)
    }
    val currentRoute by rememberUpdatedState(route)
    val roverTracking = trackingState == TrackingState.TRACKING && anchorState == TrackingState.TRACKING
    val currentTracking by rememberUpdatedState(roverTracking)
    LaunchedEffect(motion.isRunning) {
        if (!motion.isRunning) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                motion = motion.step(seconds, currentTracking, currentRoute is RoutePlan.Route)
            }
            if (!motion.isRunning) break
        }
    }

    LaunchedEffect(motion.status) {
        if (motion.status == MotionStatus.ARRIVED) {
            // Dance facing the player: towards the point on the floor under the phone.
            val anchor = floorAnchor
            val facing = if (anchor != null) {
                val player = anchor.pose.inverse().transformPoint(cameraPosition)
                headingDegrees(motion.pose.position, FloorPoint(player[0], player[2]))
            } else motion.pose.heading
            dance = RoverDance(from = motion.pose.heading, facing = facing)
        }
    }
    LaunchedEffect(dance != null) {
        if (dance == null) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                dance = dance?.step(seconds, currentTracking)
            }
            if (dance?.done != false) break
        }
        dance = null
    }

    LaunchedEffect(greeting != null) {
        if (greeting == null) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                greeting = greeting?.step(seconds, currentTracking)
            }
            if (greeting?.done != false) break
        }
        greeting = null
    }

    fun go() {
        val points = (route as? RoutePlan.Route)?.points ?: return
        motion = motion.copy(pose = roverPose).start(points)
        Log.i(TAG, "Rover started: ${points.size} waypoints")
    }

    fun stopRover() {
        motion = motion.stop()
    }

    fun onTap() {
        // Nothing is placed until the player accepts the detected floor area.
        if (setup.phase == SetupPhase.CONFIRM_AREA) return
        // Taps wait until the rover is back from greeting the player.
        if (greeting != null) return
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
            if (!greeted) {
                greeted = true
                val player = created.pose.inverse().transformPoint(cameraPosition)
                greeting = RoverGreeting.towards(FloorPoint(player[0], player[2]))
            }
            return
        }
        val local = anchor.pose.inverse().transformPoint(hit.hitPose.translation)
        val result = when (tapMode) {
            TapMode.DESTINATION -> setup.placeDestination(local[0], local[1], local[2], rover = motion.pose.position)
            TapMode.OBSTACLE -> setup.placeObstacle(local[0], local[1], local[2], rover = motion.pose.position)
        }
        when (result) {
            is PlacementResult.Accepted -> {
                setup = result.setup
                // Destination set: the next tap most likely places the obstacle.
                if (tapMode == TapMode.DESTINATION) tapMode = TapMode.OBSTACLE
                // A new destination after arriving: wait for the player to start again.
                if (motion.status == MotionStatus.ARRIVED) {
                    motion = motion.stop()
                    dance = null
                }
                Log.i(TAG, "Placed $tapMode: destination=${setup.destination} obstacle=${setup.obstacle}")
            }
            is PlacementResult.Rejected -> rejection = result.reason
        }
    }

    fun restart() {
        // Its model goes with the rover's node: load a fresh one for the next rover.
        if (floorAnchor != null) roverModelGeneration++
        floorAnchor?.detach()
        floorAnchor = null
        setup = setup.reset()
        rejection = null
        tapMode = TapMode.DESTINATION
        motion = RoverMotion()
        greeting = null
        dance = null
        routeStart = FloorPoint.ORIGIN
        // The map lives in the old anchor's frame.
        obstacleMap.clear()
        floorLevel.reset()
        mapVersion++
        overlayVersion++
    }

    /** Adds what depth sees to the obstacle map, while the floor anchor is reliable. */
    fun updateMap(frame: Frame, depthFrame: DepthFrame) {
        val anchor = floorAnchor ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        if (anchor.trackingState != TrackingState.TRACKING) return
        val intrinsics = depthIntrinsics(frame, depthFrame) ?: return
        val floorFromCamera = FloatArray(16)
        anchor.pose.inverse().compose(frame.camera.pose).toMatrix(floorFromCamera, 0)
        if (obstacleMap.integrate(floorLevel.level(projectToFloor(depthFrame, intrinsics, floorFromCamera)))) {
            mapVersion = obstacleMap.version
        }
        if (frame.timestamp - lastOverlayAtNs >= OVERLAY_UPDATE_INTERVAL_NS) {
            lastOverlayAtNs = frame.timestamp
            if (showMap) overlayVersion = obstacleMap.version
        }
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
        updateMap(frame, depthFrame)
        if (!diagnosticOn) return
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
        // A new session has new floor planes: the player must accept the area again.
        setup = FloorSetup()
        // A new game: the rover greets the player again.
        greeted = false
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
        routeNeedsScan = route is RoutePlan.NeedsScan,
        driving = motion.isRunning,
        arrived = motion.status == MotionStatus.ARRIVED,
        greeting = greeting != null,
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
                // Depth feeds the obstacle map (Part 5) and the diagnostics (Part 4).
                // ARSceneView downgrades it to DISABLED where it is not supported.
                depthMode = Config.DepthMode.AUTOMATIC,
                // ARCore silently keeps the torch off on devices that cannot drive it.
                flashMode = if (torchOn) Config.FlashMode.TORCH else Config.FlashMode.OFF,
                onSessionUpdated = { session, frame ->
                    recorder.recordFrame(session)
                    frame.camera.pose.getTranslation(cameraPosition, 0)
                    if (diagnosticOn || floorAnchor != null) updateDepth(frame)
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
                // One model per placed rover: when the anchor goes (Recomeçar), SceneView destroys the
                // model's entities along with its node, so a reused instance would come back
                // invisible. A new one is loaded as soon as the old rover leaves, before the next tap,
                // so the stand-in box is not seen in a normal game.
                val roverModel = key(roverModelGeneration) { rememberModelInstance(modelLoader, ROVER_MODEL_ASSET) }
                // Same for the flag, which leaves with the rover (Recomeçar).
                val destinationModel = key(roverModelGeneration) { rememberModelInstance(modelLoader, DESTINATION_MODEL_ASSET) }
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
                        val model = roverModel
                        // The rover turns to face where it will go: the route, or the destination.
                        Node(
                            position = Position(x = roverPose.position.x, y = dance?.lift ?: 0f, z = roverPose.position.z),
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
                                // Simple rover while the model loads (or if it fails); the sphere
                                // marks its front (-Z).
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
                            // Virtual stand-in for a real box until depth detection (Parts 4 and 5).
                            CubeNode(
                                size = Size(FloorSetup.OBSTACLE_SIZE, 0.20f, FloorSetup.OBSTACLE_SIZE),
                                center = Position(y = 0.10f),
                                position = Position(x = obstacle.x, z = obstacle.z),
                                materialInstance = obstacleMark,
                            )
                        }
                        overlayColors?.let { colors ->
                            MapOverlay(
                                colors = colors,
                                gridSize = obstacleMap.size,
                                cellSize = ROUTE_CELL_SIZE,
                                halfSize = FloorSetup.PLAY_AREA_HALF_SIZE,
                                version = overlayVersion,
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
            }
        }

        if (diagnosticOn) {
            coloredDepth?.let { DepthOverlay(it) }
        }

        val areaConfirmed = setup.areaConfirmed
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
                showGameTools = areaConfirmed,
                diagnosticOn = diagnosticOn,
                onToggleDiagnostic = { setDiagnostic(!diagnosticOn) },
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                showMap = showMap,
                onToggleMap = {
                    showMap = !showMap
                    overlayVersion++
                },
                canChooseNewArea = playbackFile == null,
                onNewArea = ::chooseNewArea,
            )
            if (diagnosticOn && areaConfirmed) {
                DiagnosticPanel(
                    report = depthReport,
                    supportText = depthLabel(depthSupport),
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
            hintText = hintLabel(hint, (route as? RoutePlan.Blocked)?.reason),
            warning = hint == SetupHint.TRACKING_LOST || hint == SetupHint.ANCHOR_LOST,
            rejectionText = rejection?.let(::rejectionLabel),
            noteText = "Sem profundidade: só o obstáculo virtual bloqueia."
                .takeIf { setup.roverPlaced && depthSupport == DepthSupport.NONE },
            searching = hint == SetupHint.SCAN_FLOOR,
            confirmingArea = hint == SetupHint.CONFIRM_AREA,
            // Only an area the circle is on can be accepted.
            canConfirm = reticleHit != null,
            // Until the rover is placed there is always a way back to scanning.
            showNewArea = setup.phase == SetupPhase.PLACE_ROVER && playbackFile == null,
            onConfirmArea = { setup = setup.confirmArea() },
            onNewArea = ::chooseNewArea,
            tapMode = tapMode.takeIf { setup.roverPlaced && greeting == null },
            onTapModeChange = {
                tapMode = it
                rejection = null
            },
            hasObstacle = setup.obstacle != null,
            onRemoveObstacle = { setup = setup.withoutObstacle() },
            showRestart = setup.roverPlaced && greeting == null,
            onRestart = ::restart,
            canGo = setup.phase == SetupPhase.READY && route is RoutePlan.Route &&
                !motion.isRunning && motion.status != MotionStatus.ARRIVED,
            driving = motion.isRunning,
            onGo = ::go,
            onStop = ::stopRover,
            // The area question is the one decision on screen: centered, so it is not missed.
            modifier = Modifier.align(if (hint == SetupHint.CONFIRM_AREA) Alignment.Center else Alignment.BottomCenter),
        )
    }
}

/** One compact line: tracking state on the left, icon switches on the right. */
@Composable
private fun StatusBar(
    tracking: Boolean,
    trackingText: String,
    showGameTools: Boolean,
    diagnosticOn: Boolean,
    onToggleDiagnostic: () -> Unit,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    showMap: Boolean,
    onToggleMap: () -> Unit,
    canChooseNewArea: Boolean,
    onNewArea: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (tracking) Color(0xFF66BB6A) else Color(0xFFFFB300), CircleShape),
        )
        // Long warnings are cut short so the icons (above all the torch) never leave the screen.
        Text(
            trackingText,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 6.dp),
        )
        if (showGameTools) {
            IconToggle(R.drawable.ic_depth, "Profundidade", diagnosticOn, onToggleDiagnostic)
            IconToggle(R.drawable.ic_map, "Mapa de obstáculos", showMap, onToggleMap)
        }
        IconToggle(R.drawable.ic_torch, "Lanterna", torchOn, onToggleTorch)
        if (showGameTools && canChooseNewArea) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconToggle(R.drawable.ic_more, "Mais opções", selected = false) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        // Starts the game over: new floor scan, rover, destination and map cleared.
                        text = { Text("Resetar jogo") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reset_game), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onNewArea()
                        },
                    )
                }
            }
        }
    }
}

/** Round icon button that turns purple while its option is on. */
@Composable
fun IconToggle(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupPanel(
    hintText: String,
    warning: Boolean,
    rejectionText: String?,
    noteText: String?,
    searching: Boolean,
    confirmingArea: Boolean,
    canConfirm: Boolean,
    showNewArea: Boolean,
    onConfirmArea: () -> Unit,
    onNewArea: () -> Unit,
    tapMode: TapMode?,
    onTapModeChange: (TapMode) -> Unit,
    hasObstacle: Boolean,
    onRemoveObstacle: () -> Unit,
    showRestart: Boolean,
    onRestart: () -> Unit,
    canGo: Boolean,
    driving: Boolean,
    onGo: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (warning) Color(0xFFB71C1C) else Color.Black
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(background.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The hint, with the main action (start or stop the rover) right beside it.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Spins while ARCore is still looking for the floor.
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                hintText,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (driving || canGo) {
                val compact = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                val (icon, label, action) =
                    if (driving) Triple(R.drawable.ic_stop, "Parar", onStop) else Triple(R.drawable.ic_play, "Ir", onGo)
                Button(onClick = action, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        listOfNotNull(noteText, rejectionText).forEach { line ->
            Text(
                line,
                color = Color(0xFFFFD54F),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (confirmingArea || showNewArea) {
            val compact = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            if (confirmingArea && !canConfirm) {
                Text(
                    "Mire o círculo no piso pontilhado para confirmar.",
                    color = Color(0xFFFFD54F),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNewArea, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                    Icon(painterResource(R.drawable.ic_new_area), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Outra área", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
                if (confirmingArea) {
                    Button(onClick = onConfirmArea, enabled = canConfirm, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                        Icon(painterResource(R.drawable.ic_confirm), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Confirmar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        if (tapMode != null || hasObstacle || showRestart) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tapMode != null) {
                    // What a tap places on the floor: exactly one is active.
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 4.dp)) {
                        val options = listOf(
                            Triple(TapMode.DESTINATION, R.drawable.ic_destination, "Destino"),
                            Triple(TapMode.OBSTACLE, R.drawable.ic_obstacle, "Obstáculo"),
                        )
                        options.forEachIndexed { index, (mode, icon, label) ->
                            SegmentedButton(
                                selected = tapMode == mode,
                                onClick = { onTapModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                modifier = Modifier.height(36.dp),
                                icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    activeBorderColor = MaterialTheme.colorScheme.primary,
                                    inactiveContainerColor = Color.Black.copy(alpha = 0.35f),
                                    inactiveContentColor = Color.White,
                                    inactiveBorderColor = Color.White.copy(alpha = 0.6f),
                                ),
                            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
                if (hasObstacle) IconToggle(R.drawable.ic_remove, "Tirar obstáculo", selected = false, onClick = onRemoveObstacle)
                if (showRestart) IconToggle(R.drawable.ic_restart, "Recomeçar", selected = false, onClick = onRestart)
            }
        }
    }
}

/** Small pill button, optionally with an icon, so the camera view stays clear. */
@Composable
fun CompactChip(label: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { res ->
            { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            labelColor = Color.White,
            iconColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

private fun trackingLabel(state: TrackingState, reason: TrackingFailureReason?): String = when (state) {
    TrackingState.TRACKING -> "Rastreando"
    TrackingState.STOPPED -> "Rastreamento parado"
    TrackingState.PAUSED -> when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Pouca luz: use a lanterna"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Mova mais devagar"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aponte para mais detalhes"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Câmera indisponível"
        else -> "Iniciando…"
    }
}

private fun depthLabel(support: DepthSupport): String = when (support) {
    DepthSupport.UNKNOWN -> "suporte: verificando"
    DepthSupport.NONE -> "sem suporte"
    DepthSupport.AUTOMATIC_ONLY -> "sem Raw Depth"
    DepthSupport.AUTOMATIC_AND_RAW -> "com Raw Depth"
}

private fun hintLabel(hint: SetupHint, blocked: NoPathReason?): String = when (hint) {
    SetupHint.TRACKING_LOST -> "Rastreamento perdido: aponte para o piso."
    SetupHint.ANCHOR_LOST -> "O rover se perdeu: toque em ↻."
    SetupHint.SCAN_FLOOR -> "Mova o celular devagar sobre o piso."
    SetupHint.CONFIRM_AREA -> "Piso encontrado. Quer jogar nesta área?"
    SetupHint.AIM_AT_FLOOR -> "Mire o círculo no piso pontilhado."
    SetupHint.TAP_TO_PLACE_ROVER -> "Toque para colocar o rover."
    SetupHint.GREETING -> "Olá! O rover veio te cumprimentar."
    SetupHint.TAP_TO_PLACE_DESTINATION -> "Toque para marcar o destino."
    SetupHint.NEEDS_SCAN -> "Aponte a câmera para o caminho."
    SetupHint.NO_ROUTE -> when (blocked) {
        NoPathReason.START_BLOCKED -> "Rover cercado: afaste o obstáculo."
        NoPathReason.GOAL_BLOCKED -> "Destino num obstáculo: mova o destino."
        else -> "Sem caminho: mova obstáculo ou destino."
    }
    SetupHint.READY -> "Rota pronta: toque em Ir."
    SetupHint.DRIVING -> "Rover a caminho…"
    SetupHint.ARRIVED -> "Chegou! Marque outro destino."
}

private fun rejectionLabel(reason: PlacementRejection): String = when (reason) {
    PlacementRejection.NO_ROVER -> "Coloque o rover primeiro."
    PlacementRejection.NOT_SAME_FLOOR -> "Precisa ficar no mesmo piso do rover."
    PlacementRejection.OUT_OF_AREA -> "Muito longe do rover."
    PlacementRejection.TOO_CLOSE -> "Muito perto do rover."
    PlacementRejection.BLOCKED_BY_OBSTACLE -> "O destino não pode ficar no obstáculo."
    PlacementRejection.COVERS_ROVER -> "O obstáculo não pode cobrir o rover."
    PlacementRejection.COVERS_DESTINATION -> "O obstáculo não pode cobrir o destino."
}
