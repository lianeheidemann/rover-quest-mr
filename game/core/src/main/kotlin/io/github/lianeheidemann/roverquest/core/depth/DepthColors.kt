package io.github.lianeheidemann.roverquest.core.depth

import kotlin.math.roundToInt

/** Depth mapped to the first color of the ramp (warm, near). */
const val COLOR_RAMP_NEAR_MM = 300

/** Depth mapped to the last color of the ramp (cool, far). */
const val COLOR_RAMP_FAR_MM = 4000

private val RAMP = intArrayOf(
    0xFFE53935.toInt(), // red, near
    0xFFFDD835.toInt(), // yellow
    0xFF43A047.toInt(), // green
    0xFF00ACC1.toInt(), // cyan
    0xFF1E88E5.toInt(), // blue, far
)

/** ARGB color for one depth value: warm near, cool far, fully transparent without data. */
fun depthColor(mm: Int): Int {
    if (mm <= 0) return 0
    val t = ((mm - COLOR_RAMP_NEAR_MM).toFloat() / (COLOR_RAMP_FAR_MM - COLOR_RAMP_NEAR_MM)).coerceIn(0f, 1f)
    val scaled = t * (RAMP.size - 1)
    val i = scaled.toInt().coerceAtMost(RAMP.size - 2)
    return lerpColor(RAMP[i], RAMP[i + 1], scaled - i)
}

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + (cb - ca) * t).roundToInt() and 0xFF
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** A colored depth image ready to draw, already turned to the display orientation. */
class ColoredDepth(val width: Int, val height: Int, val argb: IntArray)

/**
 * Colors the depth image and turns it clockwise by [quarterTurns] × 90°, to go from the camera
 * sensor orientation to the display orientation.
 */
fun colorize(frame: DepthFrame, quarterTurns: Int): ColoredDepth {
    val turns = Math.floorMod(quarterTurns, 4)
    val w = frame.width
    val h = frame.height
    val outW = if (turns % 2 == 0) w else h
    val outH = if (turns % 2 == 0) h else w
    val out = IntArray(outW * outH)
    for (y in 0 until outH) {
        for (x in 0 until outW) {
            val (sx, sy) = when (turns) {
                0 -> x to y
                1 -> y to (h - 1 - x)
                2 -> (w - 1 - x) to (h - 1 - y)
                else -> (w - 1 - y) to x
            }
            out[y * outW + x] = depthColor(frame.depthAt(sx, sy))
        }
    }
    return ColoredDepth(outW, outH, out)
}
