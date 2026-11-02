package io.github.lianeheidemann.roverquest.ui

import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.lianeheidemann.roverquest.core.floor.PlacementRejection
import io.github.lianeheidemann.roverquest.core.floor.SetupHint
import io.github.lianeheidemann.roverquest.core.nav.NoPathReason
import io.github.lianeheidemann.roverquest.core.startup.DepthSupport

// Texts shown to the player, in Portuguese.

internal fun trackingLabel(state: TrackingState, reason: TrackingFailureReason?): String = when (state) {
    TrackingState.TRACKING -> "Rastreando"
    TrackingState.STOPPED -> "Rastreamento parado"
    TrackingState.PAUSED -> when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Pouca luz: use a lanterna"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Mova mais devagar"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aponte para mais detalhes"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Câmera indisponível"
        else -> "Iniciando…"
    }
}

internal fun depthLabel(support: DepthSupport): String = when (support) {
    DepthSupport.UNKNOWN -> "suporte: verificando"
    DepthSupport.NONE -> "sem suporte"
    DepthSupport.AUTOMATIC_ONLY -> "sem Raw Depth"
    DepthSupport.AUTOMATIC_AND_RAW -> "com Raw Depth"
}

internal fun hintLabel(hint: SetupHint, blocked: NoPathReason?): String = when (hint) {
    SetupHint.TRACKING_LOST -> "Rastreamento perdido: aponte para o piso."
    SetupHint.ANCHOR_LOST -> "O rover se perdeu: toque em ↻."
    SetupHint.SCAN_FLOOR -> "Mova o celular devagar sobre o piso."
    SetupHint.CONFIRM_AREA -> "Piso encontrado. Quer jogar nesta área?"
    SetupHint.AIM_AT_FLOOR -> "Mire o círculo no piso pontilhado."
    SetupHint.TAP_TO_PLACE_ROVER -> "Toque para colocar o rover."
    SetupHint.GREETING -> "Olá! O rover veio te cumprimentar."
    SetupHint.TAP_TO_PLACE_DESTINATION -> "Toque para marcar o destino."
    SetupHint.NEEDS_SCAN -> "Aponte a câmera para o caminho."
    SetupHint.NO_ROUTE -> when (blocked) {
        NoPathReason.START_BLOCKED -> "Rover cercado: afaste o obstáculo."
        NoPathReason.GOAL_BLOCKED -> "Destino num obstáculo: mova o destino."
        else -> "Sem caminho: mova obstáculo ou destino."
    }
    SetupHint.READY -> "Rota pronta: toque em Ir."
    SetupHint.DRIVING -> "Rover a caminho…"
    SetupHint.ARRIVED -> "Chegou! Marque outro destino."
}

internal fun rejectionLabel(reason: PlacementRejection): String = when (reason) {
    PlacementRejection.NO_ROVER -> "Coloque o rover primeiro."
    PlacementRejection.NOT_SAME_FLOOR -> "Precisa ficar no mesmo piso do rover."
    PlacementRejection.OUT_OF_AREA -> "Muito longe do rover."
    PlacementRejection.TOO_CLOSE -> "Muito perto do rover."
    PlacementRejection.BLOCKED_BY_OBSTACLE -> "O destino não pode ficar no obstáculo."
    PlacementRejection.COVERS_ROVER -> "O obstáculo não pode cobrir o rover."
    PlacementRejection.COVERS_DESTINATION -> "O obstáculo não pode cobrir o destino."
}
