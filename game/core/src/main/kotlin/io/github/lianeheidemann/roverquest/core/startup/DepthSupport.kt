package io.github.lianeheidemann.roverquest.core.startup

/** Depth modes the device reported as supported by ARCore. */
enum class DepthSupport {
    UNKNOWN,
    NONE,
    AUTOMATIC_ONLY,
    AUTOMATIC_AND_RAW;

    companion object {
        fun from(automatic: Boolean, raw: Boolean): DepthSupport = when {
            automatic && raw -> AUTOMATIC_AND_RAW
            automatic -> AUTOMATIC_ONLY
            else -> NONE
        }
    }
}
