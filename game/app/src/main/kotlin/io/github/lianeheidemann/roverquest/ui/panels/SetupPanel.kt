package io.github.lianeheidemann.roverquest.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lianeheidemann.roverquest.R
import io.github.lianeheidemann.roverquest.ui.ar.TapMode
import io.github.lianeheidemann.roverquest.ui.components.IconToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupPanel(
    hintText: String,
    warning: Boolean,
    rejectionText: String?,
    noteText: String?,
    searching: Boolean,
    confirmingArea: Boolean,
    canConfirm: Boolean,
    showNewArea: Boolean,
    onConfirmArea: () -> Unit,
    onNewArea: () -> Unit,
    tapMode: TapMode?,
    onTapModeChange: (TapMode) -> Unit,
    hasObstacle: Boolean,
    onRemoveObstacle: () -> Unit,
    showRestart: Boolean,
    onRestart: () -> Unit,
    canGo: Boolean,
    driving: Boolean,
    onGo: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (warning) Color(0xFFB71C1C) else Color.Black
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(background.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The hint, with the main action (start or stop the rover) right beside it.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Spins while ARCore is still looking for the floor.
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                hintText,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (driving || canGo) {
                val compact = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                val (icon, label, action) =
                    if (driving) Triple(R.drawable.ic_stop, "Parar", onStop) else Triple(R.drawable.ic_play, "Ir", onGo)
                Button(onClick = action, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        listOfNotNull(noteText, rejectionText).forEach { line ->
            Text(
                line,
                color = Color(0xFFFFD54F),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (confirmingArea || showNewArea) {
            val compact = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            if (confirmingArea && !canConfirm) {
                Text(
                    "Mire o círculo no piso pontilhado para confirmar.",
                    color = Color(0xFFFFD54F),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNewArea, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                    Icon(painterResource(R.drawable.ic_new_area), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Outra área", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
                if (confirmingArea) {
                    Button(onClick = onConfirmArea, enabled = canConfirm, contentPadding = compact, modifier = Modifier.height(36.dp)) {
                        Icon(painterResource(R.drawable.ic_confirm), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Confirmar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        if (tapMode != null || hasObstacle || showRestart) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tapMode != null) {
                    // What a tap places on the floor: exactly one is active.
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 4.dp)) {
                        val options = listOf(
                            Triple(TapMode.DESTINATION, R.drawable.ic_destination, "Destino"),
                            Triple(TapMode.OBSTACLE, R.drawable.ic_obstacle, "Obstáculo"),
                        )
                        options.forEachIndexed { index, (mode, icon, label) ->
                            SegmentedButton(
                                selected = tapMode == mode,
                                onClick = { onTapModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                modifier = Modifier.height(36.dp),
                                icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    activeBorderColor = MaterialTheme.colorScheme.primary,
                                    inactiveContainerColor = Color.Black.copy(alpha = 0.35f),
                                    inactiveContentColor = Color.White,
                                    inactiveBorderColor = Color.White.copy(alpha = 0.6f),
                                ),
                            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
                if (hasObstacle) IconToggle(R.drawable.ic_remove, "Tirar obstáculo", selected = false, onClick = onRemoveObstacle)
                if (showRestart) IconToggle(R.drawable.ic_restart, "Recomeçar", selected = false, onClick = onRestart)
            }
        }
    }
}
