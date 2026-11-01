package io.github.lianeheidemann.roverquest.core.depth

/**
 * One depth image as plain data, in the camera sensor's orientation.
 *
 * @param depthMm depth per pixel in millimeters, row by row; `0` means "no data".
 * @param confidence optional confidence per pixel, `0..255` (ARCore raw depth confidence),
 *   same size as [depthMm].
 */
class DepthFrame(
    val width: Int,
    val height: Int,
    val depthMm: IntArray,
    val confidence: IntArray? = null,
) {
    init {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(depthMm.size == width * height) { "depthMm must have width * height values" }
        require(confidence == null || confidence.size == depthMm.size) {
            "confidence must have the same size as depthMm"
        }
    }

    fun depthAt(x: Int, y: Int): Int = depthMm[y * width + x]

    /** Fraction of pixels that carry a depth value. */
    val coverage: Float
        get() = depthMm.count { it > 0 }.toFloat() / depthMm.size

    /**
     * Median depth around a point given in normalized coordinates (`0..1`, `0.5, 0.5` is the
     * center), using a square window of `2 * radius + 1` pixels. Pixels without data are ignored.
     */
    fun sampleAt(u: Float, v: Float, radius: Int = 2): DepthSample {
        val cx = (u * (width - 1)).toInt().coerceIn(0, width - 1)
        val cy = (v * (height - 1)).toInt().coerceIn(0, height - 1)
        val depths = mutableListOf<Int>()
        val confidences = mutableListOf<Int>()
        for (y in (cy - radius)..(cy + radius)) {
            for (x in (cx - radius)..(cx + radius)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val index = y * width + x
                val mm = depthMm[index]
                if (mm <= 0) continue
                depths.add(mm)
                confidence?.let { confidences.add(it[index]) }
            }
        }
        if (depths.isEmpty()) return DepthSample(meters = null, confidence = null)
        depths.sort()
        val meters = depths[depths.size / 2] / 1000f
        val meanConfidence = if (confidences.isEmpty()) null else confidences.average().toFloat() / 255f
        return DepthSample(meters, meanConfidence)
    }
}

/**
 * Depth at one spot of the image.
 *
 * @param meters median depth, or `null` when no pixel around the spot had data.
 * @param confidence mean confidence `0..1`, or `null` when confidence is not available.
 */
data class DepthSample(val meters: Float?, val confidence: Float?)
