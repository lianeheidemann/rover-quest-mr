package io.github.lianeheidemann.roverquest.ui.ar

import android.media.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.depth.CameraIntrinsics
import io.github.lianeheidemann.roverquest.core.depth.ColoredDepth
import io.github.lianeheidemann.roverquest.core.depth.DepthFrame
import io.github.lianeheidemann.roverquest.core.depth.DepthQuality
import io.github.lianeheidemann.roverquest.core.depth.DepthReport
import io.github.lianeheidemann.roverquest.core.depth.FloorLevel
import io.github.lianeheidemann.roverquest.core.depth.analyzeDepth
import io.github.lianeheidemann.roverquest.core.depth.colorize
import io.github.lianeheidemann.roverquest.core.depth.projectToFloor
import io.github.lianeheidemann.roverquest.core.nav.newObstacleMap
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import java.nio.ByteOrder

/** Depth is read and drawn at most 10 times per second. */
private const val DEPTH_UPDATE_INTERVAL_NS = 100_000_000L

/** Without a new depth image for this long, the reading is reported as missing. */
private const val DEPTH_STALE_AFTER_NS = 1_500_000_000L

/** The debug map overlay is rebuilt at most once per second: it is many small quads. */
private const val OVERLAY_UPDATE_INTERVAL_NS = 1_000_000_000L

/**
 * Reads ARCore depth and feeds it to `:core`: the obstacle map (Part 5), in the floor anchor's
 * frame, and the diagnostics overlay (Part 4). Runs on the ARCore frame thread.
 */
@Stable
internal class DepthMapper {
    /** What the device offers; without depth the route falls back to the manual obstacle only. */
    var support by mutableStateOf(DepthSupport.UNKNOWN)

    val mapping: Boolean
        get() = support == DepthSupport.AUTOMATIC_ONLY || support == DepthSupport.AUTOMATIC_AND_RAW

    val obstacleMap = newObstacleMap()

    /** Heights are measured from the floor depth sees, not from the anchor (see FloorLevel). */
    val floorLevel = FloorLevel()

    /** Grows when the obstacle map changes, to replan. */
    var mapVersion by mutableIntStateOf(0)
        private set

    /** Grows when the map overlay should be rebuilt. */
    var overlayVersion by mutableIntStateOf(0)
        private set
    private var lastOverlayAtNs = 0L

    var report by mutableStateOf(DepthReport(DepthQuality.WAITING))
        private set
    var colored by mutableStateOf<ColoredDepth?>(null)
        private set
    private var lastDepthAtNs = 0L

    private val available: Boolean get() = support != DepthSupport.NONE

    /** Reads depth at most every [DEPTH_UPDATE_INTERVAL_NS]. */
    fun update(frame: Frame, anchor: Anchor?, diagnostics: Boolean, showMap: Boolean) {
        if (frame.timestamp - lastDepthAtNs < DEPTH_UPDATE_INTERVAL_NS) return
        val depthFrame = readDepthFrame(frame)
        if (depthFrame == null) {
            // No image this frame: keep the last one for a moment, then say depth is missing.
            if (frame.timestamp - lastDepthAtNs > DEPTH_STALE_AFTER_NS) {
                report = analyzeDepth(available, null)
                colored = null
            }
            return
        }
        lastDepthAtNs = frame.timestamp
        if (anchor != null) updateMap(frame, anchor, depthFrame, showMap)
        if (!diagnostics) return
        report = analyzeDepth(available, depthFrame)
        // The app is portrait-only: the sensor image is a quarter turn clockwise from the display.
        colored = colorize(depthFrame, quarterTurns = 1)
    }

    /** Adds what depth sees to the obstacle map, while the floor anchor is reliable. */
    private fun updateMap(frame: Frame, anchor: Anchor, depthFrame: DepthFrame, showMap: Boolean) {
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

    /** Forgets the diagnostics reading, e.g. when they are switched on or off. */
    fun clearReport() {
        report = analyzeDepth(available, null)
        colored = null
        lastDepthAtNs = 0L
    }

    /** A new floor anchor: the map and the floor estimate lived in the old anchor's frame. */
    fun resetMap() {
        obstacleMap.clear()
        floorLevel.reset()
        mapVersion++
        refreshOverlay()
    }

    fun refreshOverlay() {
        overlayVersion++
    }
}

@Composable
internal fun rememberDepthMapper(): DepthMapper = remember { DepthMapper() }

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

/**
 * Copies the latest ARCore depth image (and the raw depth confidence, when available) into a
 * [DepthFrame]. Returns `null` when no depth image is available for this frame.
 * Must run on the thread that delivers ARCore frames (`onSessionUpdated`).
 */
private fun readDepthFrame(frame: Frame): DepthFrame? {
    val depth = runCatching { frame.acquireDepthImage16Bits() }.getOrNull() ?: return null
    return depth.use { image ->
        val width = image.width
        val height = image.height
        val depthMm = readPlane(image, bytesPerPixel = 2)
        val confidence = runCatching { frame.acquireRawDepthConfidenceImage() }.getOrNull()?.use { conf ->
            if (conf.width == width && conf.height == height) readPlane(conf, bytesPerPixel = 1) else null
        }
        DepthFrame(width, height, depthMm, confidence)
    }
}

/** Reads a single-plane image of unsigned 8- or 16-bit (little-endian) pixels, row by row. */
private fun readPlane(image: Image, bytesPerPixel: Int): IntArray {
    val plane = image.planes[0]
    val buffer = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val out = IntArray(image.width * image.height)
    for (y in 0 until image.height) {
        val rowStart = y * plane.rowStride
        for (x in 0 until image.width) {
            val offset = rowStart + x * plane.pixelStride
            out[y * image.width + x] = if (bytesPerPixel == 2) {
                buffer.getShort(offset).toInt() and 0xFFFF
            } else {
                buffer.get(offset).toInt() and 0xFF
            }
        }
    }
    return out
}

