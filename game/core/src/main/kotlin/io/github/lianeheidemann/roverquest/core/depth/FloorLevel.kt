package io.github.lianeheidemann.roverquest.core.depth

import kotlin.math.abs

/** Only points this close to the current floor estimate, in meters, may be floor. */
const val FLOOR_SEARCH_RANGE = 0.15f

/** Points this close to the fitted floor, in meters, are used to refine it. */
const val FLOOR_FIT_RANGE = 0.04f

/** Fewer floor points than this in a frame and the previous floor is kept. */
const val MIN_FLOOR_POINTS = 200

/** Largest accepted floor slope (rise per meter, about 4.5 degrees). Steeper is not a floor. */
const val MAX_FLOOR_SLOPE = 0.08f

/** Largest accepted floor offset from the anchor, in meters. */
const val MAX_FLOOR_OFFSET = 0.15f

/**
 * The floor as depth sees it, in the floor anchor's frame: `height = a + b·x + c·z`.
 *
 * Heights from depth are measured from the anchor, but the anchor can sit a few centimeters off
 * the real floor (after the phone shakes) and depth errors grow with distance. Far from the
 * anchor that alone lifts the floor past the obstacle threshold, so plain floor turns into
 * obstacles. [level] fits the floor seen in each frame and gives heights above it instead.
 */
class FloorLevel {
    var a = 0f
        private set
    var b = 0f
        private set
    var c = 0f
        private set

    private fun floorAt(x: Float, z: Float) = a + b * x + c * z

    /** Back to the anchor's own floor, for a new anchor. */
    fun reset() {
        a = 0f; b = 0f; c = 0f
    }

    /**
     * Refits the floor from one frame of [samples] (when it can) and returns them with heights
     * above that floor.
     */
    fun level(samples: List<FloorSample>): List<FloorSample> {
        fit(samples)
        return samples.map { it.copy(height = it.height - floorAt(it.x, it.z)) }
    }

    private fun fit(samples: List<FloorSample>) {
        val candidates = samples.filter { abs(it.height - floorAt(it.x, it.z)) <= FLOOR_SEARCH_RANGE }
        if (candidates.size < MIN_FLOOR_POINTS) return

        // Start from the current tilt with the median offset: the floor is most of the points, so
        // boxes and furniture do not move the median much.
        val residuals = candidates.map { it.height - floorAt(it.x, it.z) }.sorted()
        var fa = a + residuals[residuals.size / 2]
        var fb = b
        var fc = c
        repeat(2) {
            val inliers = candidates.filter { abs(it.height - (fa + fb * it.x + fc * it.z)) <= FLOOR_FIT_RANGE }
            if (inliers.size < MIN_FLOOR_POINTS) return
            val plane = fitPlane(inliers) ?: return
            fa = plane[0]; fb = plane[1]; fc = plane[2]
        }
        if (abs(fb) > MAX_FLOOR_SLOPE || abs(fc) > MAX_FLOOR_SLOPE || abs(fa) > MAX_FLOOR_OFFSET) return
        a = fa; b = fb; c = fc
    }
}

/** Least squares `height = a + b·x + c·z`; `null` when the points do not pin a plane down. */
private fun fitPlane(points: List<FloorSample>): FloatArray? {
    // Centered sums keep the 3×3 normal equations well conditioned.
    val n = points.size.toDouble()
    val mx = points.sumOf { it.x.toDouble() } / n
    val mz = points.sumOf { it.z.toDouble() } / n
    val mh = points.sumOf { it.height.toDouble() } / n
    var sxx = 0.0; var sxz = 0.0; var szz = 0.0; var sxh = 0.0; var szh = 0.0
    for (p in points) {
        val dx = p.x - mx
        val dz = p.z - mz
        val dh = p.height - mh
        sxx += dx * dx; sxz += dx * dz; szz += dz * dz
        sxh += dx * dh; szh += dz * dh
    }
    val det = sxx * szz - sxz * sxz
    if (abs(det) < 1e-9) return null
    val b = (sxh * szz - szh * sxz) / det
    val c = (szh * sxx - sxh * sxz) / det
    val a = mh - b * mx - c * mz
    return floatArrayOf(a.toFloat(), b.toFloat(), c.toFloat())
}
