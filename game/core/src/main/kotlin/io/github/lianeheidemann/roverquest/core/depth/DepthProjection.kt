package io.github.lianeheidemann.roverquest.core.depth

/** Pinhole camera intrinsics, in pixels of the depth image. */
data class CameraIntrinsics(val fx: Float, val fy: Float, val cx: Float, val cy: Float) {
    init {
        require(fx > 0f && fy > 0f) { "focal lengths must be positive" }
    }
}

/**
 * A real-world point seen by the depth camera, in the floor anchor's frame: [x] and [z] on the
 * floor (same axes as [io.github.lianeheidemann.roverquest.core.floor.FloorPoint]) and [height]
 * above it.
 */
data class FloorSample(val x: Float, val z: Float, val height: Float)

/**
 * Depth is only trusted between these distances, in meters, for mapping. Far away the floor is
 * seen at a grazing angle and small depth errors turn into large height errors.
 */
const val MIN_MAPPING_DEPTH = 0.3f
const val MAX_MAPPING_DEPTH = 2.5f

/** Pixels below this confidence (`0..1`) are skipped when confidence is available. */
const val MIN_MAPPING_CONFIDENCE = 0.5f

/**
 * Turns a depth image into points on and above the floor.
 *
 * Uses ARCore's camera convention: the camera looks down its -Z axis, +Y is up in the image,
 * and a pixel `(u, v)` at depth `d` sits at `((u - cx)·d/fx, -(v - cy)·d/fy, -d)`.
 *
 * @param floorFromCamera 4×4 column-major matrix taking camera-space points to the floor
 *   anchor's frame (`anchor.pose.inverse() * camera.pose`).
 * @param stride only every `stride`-th pixel in each direction is used, to keep this cheap.
 */
fun projectToFloor(
    frame: DepthFrame,
    intrinsics: CameraIntrinsics,
    floorFromCamera: FloatArray,
    stride: Int = 2,
): List<FloorSample> {
    require(floorFromCamera.size == 16) { "floorFromCamera must be a 4x4 matrix" }
    require(stride >= 1) { "stride must be at least 1" }
    val m = floorFromCamera
    val minConfidence = (MIN_MAPPING_CONFIDENCE * 255).toInt()
    val samples = ArrayList<FloorSample>()
    for (v in 0 until frame.height step stride) {
        for (u in 0 until frame.width step stride) {
            val index = v * frame.width + u
            val mm = frame.depthMm[index]
            if (mm <= 0) continue
            val d = mm / 1000f
            if (d < MIN_MAPPING_DEPTH || d > MAX_MAPPING_DEPTH) continue
            val confidence = frame.confidence
            if (confidence != null && confidence[index] < minConfidence) continue

            val cx = (u - intrinsics.cx) * d / intrinsics.fx
            val cy = -(v - intrinsics.cy) * d / intrinsics.fy
            val cz = -d
            // Column-major: element (row r, column c) is m[c * 4 + r].
            val x = m[0] * cx + m[4] * cy + m[8] * cz + m[12]
            val y = m[1] * cx + m[5] * cy + m[9] * cz + m[13]
            val z = m[2] * cx + m[6] * cy + m[10] * cz + m[14]
            samples.add(FloorSample(x = x, z = z, height = y))
        }
    }
    return samples
}
