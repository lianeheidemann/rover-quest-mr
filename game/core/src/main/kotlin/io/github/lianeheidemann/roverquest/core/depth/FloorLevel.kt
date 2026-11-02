package io.github.lianeheidemann.roverquest.core.depth

import kotlin.math.abs

/**
 * Only points this close to the anchor's level (following the current tilt), in meters, may be
 * floor. Tied to the anchor, not to the last estimate, so the estimate cannot drift away step by
 * step (for example sinking into reflections on a shiny floor).
 */
const val FLOOR_SEARCH_RANGE = 0.15f

/** Points this close to the fitted floor, in meters, are used to refine it. */
const val FLOOR_FIT_RANGE = 0.04f

/** Height bins, in meters, used to find the most common height: the floor. */
private const val FLOOR_BIN = 0.01f

/** Fewer floor points than this in a frame and the previous floor is kept. */
const val MIN_FLOOR_POINTS = 200

/**
 * Largest accepted floor slope (rise per meter, about 2.3 degrees). ARCore's floor follows
 * gravity, so only depth errors tilt it; steeper is not the floor.
 */
const val MAX_FLOOR_SLOPE = 0.04f

/**
 * Largest accepted floor offset from the anchor, in meters. The anchor sits on ARCore's floor
 * plane, which on a shiny floor can be several centimeters off the floor depth sees.
 */
const val MAX_FLOOR_OFFSET = 0.15f

/** A height bin with at least this share of the tallest bin's points may be the floor. */
const val FLOOR_PEAK_SHARE = 0.3f

/** Accepted fits needed before the floor estimate is trusted (see [FloorLevel.settled]). */
const val FLOOR_FITS_TO_SETTLE = 3

/** Share of the way the floor estimate moves towards each new fit, so one bad frame does little. */
const val FLOOR_SMOOTHING = 0.2f

/**
 * The floor as depth sees it, in the floor anchor's frame: `height = a + b·x + c·z`.
 *
 * Heights from depth are measured from the anchor, but the anchor can sit a few centimeters off
 * the real floor (after the phone shakes) and depth errors grow with distance. Far from the
 * anchor that alone lifts the floor past the obstacle threshold, so plain floor turns into
 * obstacles. [level] fits the floor seen in each frame and gives heights above it instead.
 *
 * The floor is the most common height near the anchor's level: reflections on a shiny floor
 * (read too far, so below it) and boxes (above it) are fewer and spread out, so they do not move
 * that peak.
 */
class FloorLevel {
    var a = 0f
        private set
    var b = 0f
        private set
    var c = 0f
        private set

    private var fits = 0

    /**
     * The floor has been fitted a few times: until then heights are not trustworthy, and an
     * obstacle map fed with them would paint plain floor as obstacles from the first frames.
     */
    val settled: Boolean get() = fits >= FLOOR_FITS_TO_SETTLE

    private fun floorAt(x: Float, z: Float) = a + b * x + c * z

    /** Back to the anchor's own floor, for a new anchor. */
    fun reset() {
        a = 0f; b = 0f; c = 0f
        fits = 0
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
        // Heights against the anchor's level, keeping only the current tilt.
        fun tilted(s: FloorSample) = s.height - (b * s.x + c * s.z)
        val candidates = samples.filter { abs(tilted(it)) <= FLOOR_SEARCH_RANGE }
        if (candidates.size < MIN_FLOOR_POINTS) return

        // Most common height: the floor.
        val bins = ((2 * FLOOR_SEARCH_RANGE) / FLOOR_BIN).toInt() + 1
        val counts = IntArray(bins)
        for (s in candidates) {
            val i = ((tilted(s) + FLOOR_SEARCH_RANGE) / FLOOR_BIN).toInt().coerceIn(0, bins - 1)
            counts[i]++
        }
        // Among the strong peaks (the floor, but also maybe a wide low box or a rug), the one
        // closest to the anchor's level: the anchor was placed on the floor.
        val strongest = counts.max()
        val center = (bins - 1) / 2f
        val peak = counts.indices
            .filter { counts[it] >= FLOOR_PEAK_SHARE * strongest }
            .minBy { abs(it - center) }
        var fa = -FLOOR_SEARCH_RANGE + (peak + 0.5f) * FLOOR_BIN
        var fb = b
        var fc = c
        repeat(3) {
            val inliers = candidates.filter { abs(it.height - (fa + fb * it.x + fc * it.z)) <= FLOOR_FIT_RANGE }
            if (inliers.size < MIN_FLOOR_POINTS) return
            val plane = fitPlane(inliers) ?: return
            fa = plane[0]; fb = plane[1]; fc = plane[2]
        }
        if (abs(fb) > MAX_FLOOR_SLOPE || abs(fc) > MAX_FLOOR_SLOPE || abs(fa) > MAX_FLOOR_OFFSET) return
        // The first fit is taken as is; later ones only nudge the estimate.
        val k = if (fits == 0) 1f else FLOOR_SMOOTHING
        a += (fa - a) * k
        b += (fb - b) * k
        c += (fc - c) * k
        fits++
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
