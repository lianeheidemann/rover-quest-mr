package io.github.lianeheidemann.roverquest.core.depth

/** How trustworthy the current depth reading is. */
enum class DepthQuality {
    /** The device or session does not provide depth. */
    NOT_SUPPORTED,

    /** Depth is supported but no image has arrived yet (ARCore needs camera motion first). */
    WAITING,

    /** An image arrived, but too little of it or the aimed spot is reliable. */
    UNCERTAIN,

    /** The aimed spot has a depth value that can be trusted. */
    GOOD,
}

/** Why a reading is [DepthQuality.UNCERTAIN]. */
enum class DepthIssue { LOW_COVERAGE, NO_DATA_AT_CENTER, LOW_CONFIDENCE, OUT_OF_RANGE }

data class DepthReport(
    val quality: DepthQuality,
    val center: DepthSample? = null,
    val coverage: Float = 0f,
    val issue: DepthIssue? = null,
)

/** Below this fraction of pixels with data, the image as a whole is not trusted. */
const val MIN_DEPTH_COVERAGE = 0.5f

/** Below this mean confidence (`0..1`), the aimed spot is not trusted. */
const val MIN_DEPTH_CONFIDENCE = 0.5f

/** Depth is only reliable within this range, in meters. */
const val MIN_RELIABLE_DEPTH = 0.3f
const val MAX_RELIABLE_DEPTH = 5f

/**
 * Judges the latest depth image, reading the depth at the center of the screen (where the
 * placement reticle aims).
 */
fun analyzeDepth(supported: Boolean, frame: DepthFrame?): DepthReport {
    if (!supported) return DepthReport(DepthQuality.NOT_SUPPORTED)
    if (frame == null) return DepthReport(DepthQuality.WAITING)

    val coverage = frame.coverage
    val center = frame.sampleAt(0.5f, 0.5f)
    val meters = center.meters
    val issue = when {
        coverage < MIN_DEPTH_COVERAGE -> DepthIssue.LOW_COVERAGE
        meters == null -> DepthIssue.NO_DATA_AT_CENTER
        center.confidence != null && center.confidence < MIN_DEPTH_CONFIDENCE -> DepthIssue.LOW_CONFIDENCE
        meters < MIN_RELIABLE_DEPTH || meters > MAX_RELIABLE_DEPTH -> DepthIssue.OUT_OF_RANGE
        else -> null
    }
    val quality = if (issue == null) DepthQuality.GOOD else DepthQuality.UNCERTAIN
    return DepthReport(quality, center, coverage, issue)
}
