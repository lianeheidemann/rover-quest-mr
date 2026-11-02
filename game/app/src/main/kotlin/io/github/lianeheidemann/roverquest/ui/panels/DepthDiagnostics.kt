package io.github.lianeheidemann.roverquest.ui.panels

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lianeheidemann.roverquest.core.depth.ColoredDepth
import io.github.lianeheidemann.roverquest.core.depth.DepthIssue
import io.github.lianeheidemann.roverquest.core.depth.DepthQuality
import io.github.lianeheidemann.roverquest.core.depth.DepthReport
import io.github.lianeheidemann.roverquest.ui.components.CompactChip
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/** False-color depth drawn over the camera feed: red is near, blue is far, no data is clear. */
@Composable
internal fun DepthOverlay(colored: ColoredDepth, modifier: Modifier = Modifier) {
    val bitmap = remember(colored) {
        Bitmap.createBitmap(colored.argb, colored.width, colored.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.55f,
        modifier = modifier.fillMaxSize(),
    )
}

/** Two short lines: the reading at the screen center and a legend, with small recording buttons. */
@Composable
internal fun DiagnosticPanel(
    report: DepthReport,
    supportText: String,
    recording: Boolean,
    recordingError: String?,
    playingBack: Boolean,
    canReplay: Boolean,
    onToggleRecording: () -> Unit,
    onToggleReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ok = report.quality == DepthQuality.GOOD
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildString {
                    append(centerLabel(report))
                    append(" · ")
                },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Text(
                qualityLabel(report),
                color = if (ok) Color(0xFFA5D6A7) else Color(0xFFFFD54F),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!playingBack) {
                CompactChip(if (recording) "■ Parar" else "● Gravar", recording, onClick = onToggleRecording)
            }
            if (playingBack || (canReplay && !recording)) {
                CompactChip(if (playingBack) "Ao vivo" else "Repetir", playingBack, onClick = onToggleReplay)
            }
        }
        val extra = when {
            recordingError != null -> "Falha na gravação: $recordingError"
            playingBack -> "Repetindo a gravação (sem câmera ao vivo)"
            else -> null
        }
        Text(
            extra ?: "vermelho perto · azul longe · $supportText",
            color = if (extra != null) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun centerLabel(report: DepthReport): String {
    val center = report.center
    val meters = center?.meters ?: return "sem leitura"
    val distance = String.format(PT_BR, "%.2f m", meters)
    val confidence = center.confidence?.let { " · ${percent(it)}" }.orEmpty()
    return "$distance$confidence"
}

private fun qualityLabel(report: DepthReport): String = when (report.quality) {
    DepthQuality.NOT_SUPPORTED -> "sem profundidade"
    DepthQuality.WAITING -> "mova o celular"
    DepthQuality.GOOD -> "confiável"
    DepthQuality.UNCERTAIN -> when (report.issue) {
        DepthIssue.LOW_COVERAGE -> "incerta: pouca área"
        DepthIssue.NO_DATA_AT_CENTER -> "incerta: sem dado"
        DepthIssue.LOW_CONFIDENCE -> "incerta: baixa confiança"
        DepthIssue.OUT_OF_RANGE -> "incerta: fora de 0,3–5 m"
        null -> "incerta"
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
