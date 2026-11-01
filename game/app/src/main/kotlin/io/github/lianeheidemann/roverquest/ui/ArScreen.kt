package io.github.lianeheidemann.roverquest.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport
import io.github.sceneview.ar.ARSceneView

private const val TAG = "RoverQuest"

@Composable
fun ArScreen(onSessionFailed: (String) -> Unit) {
    var trackingState by remember { mutableStateOf(TrackingState.PAUSED) }
    var failureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var depthSupport by remember { mutableStateOf(DepthSupport.UNKNOWN) }

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            onSessionCreated = { session ->
                val support = DepthSupport.from(
                    automatic = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC),
                    raw = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY),
                )
                depthSupport = support
                Log.i(TAG, "Depth support: $support")
            },
            onSessionUpdated = { _, frame ->
                val state = frame.camera.trackingState
                if (state != trackingState) trackingState = state
            },
            onTrackingFailureChanged = { failureReason = it },
            onSessionFailed = { exception ->
                Log.e(TAG, "AR session failed", exception)
                onSessionFailed(exception.message ?: exception::class.java.simpleName)
            },
        )

        StatusPanel(
            trackingText = trackingLabel(trackingState, failureReason),
            depthText = depthLabel(depthSupport),
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun StatusPanel(trackingText: String, depthText: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(trackingText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(depthText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun trackingLabel(state: TrackingState, reason: TrackingFailureReason?): String = when (state) {
    TrackingState.TRACKING -> "Rastreamento: ativo"
    TrackingState.STOPPED -> "Rastreamento: parado"
    TrackingState.PAUSED -> when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Rastreamento: pouca luz"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Rastreamento: mova o celular mais devagar"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Rastreamento: aponte para uma área com mais detalhes"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Rastreamento: câmera indisponível"
        else -> "Rastreamento: mova o celular devagar para começar"
    }
}

private fun depthLabel(support: DepthSupport): String = when (support) {
    DepthSupport.UNKNOWN -> "Profundidade: verificando…"
    DepthSupport.NONE -> "Profundidade: não suportada"
    DepthSupport.AUTOMATIC_ONLY -> "Profundidade: suportada (sem Raw Depth)"
    DepthSupport.AUTOMATIC_AND_RAW -> "Profundidade: suportada (com Raw Depth)"
}
