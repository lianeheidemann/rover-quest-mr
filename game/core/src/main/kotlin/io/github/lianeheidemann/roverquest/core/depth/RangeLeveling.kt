package io.github.lianeheidemann.roverquest.core.depth

import kotlin.math.abs

/** Width of the camera-distance bands, in meters, whose floor level is measured separately. */
const val RANGE_BAND = 0.25f

/** Only points this close to the fitted floor, in meters, may be floor within a band. */
const val RANGE_WINDOW = 0.10f

/** A band needs this many floor candidates to measure its own floor level. */
const val MIN_BAND_POINTS = 40

/** Height bins, in meters, for the most common height in a band. */
private const val RANGE_BIN = 0.01f

/** A height bin with at least this share of the band's tallest bin may be the floor. */
private const val RANGE_PEAK_SHARE = 0.3f

/**
 * Removes what is left of the floor's height after the plane fit, band by band of distance from
 * the camera (one frame).
 *
 * Depth errors grow with the distance from the camera, not from the anchor, and they move with
 * the phone: one plane in the anchor's frame cannot follow them, so plain floor 1–2 m away came
 * out a few centimeters high and turned into obstacles. In each band of [RANGE_BAND] the floor is
 * the strong height peak closest to the fitted floor, and heights are given above it; between
 * bands the level is interpolated. Points without a known depth are left as they are.
 *
 * @param samples heights already measured from the fitted floor plane ([FloorLevel]).
 */
fun levelByRange(samples: List<FloorSample>): List<FloorSample> {
    val bands = HashMap<Int, MutableList<Float>>()
    for (s in samples) {
        if (s.depth <= 0f || abs(s.height) > RANGE_WINDOW) continue
        bands.getOrPut((s.depth / RANGE_BAND).toInt()) { ArrayList() }.add(s.height)
    }
    val levels = HashMap<Int, Float>()
    for ((band, heights) in bands) {
        if (heights.size >= MIN_BAND_POINTS) levels[band] = floorPeak(heights)
    }
    if (levels.isEmpty()) return samples
    return samples.map { s ->
        if (s.depth <= 0f) s else s.copy(height = s.height - levelAt(levels, s.depth))
    }
}

/** The strong peak of [heights] closest to 0, refined to the mean of the heights near it. */
private fun floorPeak(heights: List<Float>): Float {
    val bins = ((2 * RANGE_WINDOW) / RANGE_BIN).toInt() + 1
    val counts = IntArray(bins)
    for (h in heights) counts[((h + RANGE_WINDOW) / RANGE_BIN).toInt().coerceIn(0, bins - 1)]++
    val strongest = counts.max()
    val center = (bins - 1) / 2f
    val peak = counts.indices.filter { counts[it] >= RANGE_PEAK_SHARE * strongest }.minBy { abs(it - center) }
    val peakHeight = -RANGE_WINDOW + (peak + 0.5f) * RANGE_BIN
    val near = heights.filter { abs(it - peakHeight) <= 2 * RANGE_BIN }
    return if (near.isEmpty()) peakHeight else near.average().toFloat()
}

/** Floor level at [depth]: linear between measured band centers, flat beyond the first and last. */
private fun levelAt(levels: Map<Int, Float>, depth: Float): Float {
    val centers = levels.keys.sorted()
    val position = depth / RANGE_BAND - 0.5f // band b is centered at (b + 0.5) · RANGE_BAND
    if (position <= centers.first()) return levels.getValue(centers.first())
    if (position >= centers.last()) return levels.getValue(centers.last())
    val upper = centers.first { it >= position }
    val lower = centers.last { it <= position }
    if (upper == lower) return levels.getValue(lower)
    val t = (position - lower) / (upper - lower)
    return levels.getValue(lower) + (levels.getValue(upper) - levels.getValue(lower)) * t
}
