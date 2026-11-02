package io.github.lianeheidemann.roverquest.ui.ar

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Session
import io.github.sceneview.ar.recording.ARRecorder
import io.github.sceneview.ar.recording.rememberARRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the ARCore session to an MP4 and replays it, to repeat tests without moving the phone
 * (Part 4). Files go to an app-private folder: no storage permission needed.
 */
@Stable
internal class SessionRecordings(private val recorder: ARRecorder, context: Context) {
    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "ar-recordings").apply { mkdirs() }

    /** The most recent recording, or `null` when none was made yet. */
    var last by mutableStateOf(newest())
        private set

    /** The recording being replayed instead of the live camera, or `null` when live. */
    var playback by mutableStateOf<File?>(null)

    val recording: Boolean get() = recorder.state == ARRecorder.State.RECORDING

    val error: String?
        get() = recorder.errorMessage.takeIf {
            recorder.state == ARRecorder.State.ERROR || recorder.state == ARRecorder.State.IO_ERROR
        }

    /** Feeds one frame to the recording, when one is running. */
    fun record(session: Session) = recorder.recordFrame(session)

    fun toggle() {
        if (recording) {
            stop()
        } else {
            val name = "session-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.mp4"
            recorder.start(File(dir, name))
        }
    }

    fun stop() {
        if (!recording) return
        recorder.stop()
        last = newest()
        Log.i(TAG, "Recording saved: $last")
    }

    private fun newest(): File? =
        dir.listFiles { file -> file.extension == "mp4" && file.length() > 0 }?.maxByOrNull { it.lastModified() }
}

@Composable
internal fun rememberSessionRecordings(): SessionRecordings {
    val recorder = rememberARRecorder()
    val context = LocalContext.current
    return remember(recorder) { SessionRecordings(recorder, context) }
}
