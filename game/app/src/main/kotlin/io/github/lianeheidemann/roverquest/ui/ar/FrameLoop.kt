package io.github.lianeheidemann.roverquest.ui.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

/** Longest time slice, in seconds, given to one frame: a stall does not make things jump. */
private const val MAX_FRAME_SECONDS = 0.1f

/**
 * Calls [onFrame] with the seconds since the previous frame, once per frame, while [active] and
 * until it returns `false`. Drives the rover, the greeting and the dance.
 */
@Composable
internal fun RunEachFrame(active: Boolean, onFrame: (seconds: Float) -> Boolean) {
    val latest by rememberUpdatedState(onFrame)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        var keepGoing = true
        while (keepGoing) {
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(MAX_FRAME_SECONDS)
                last = now
                keepGoing = latest(seconds)
            }
        }
    }
}
