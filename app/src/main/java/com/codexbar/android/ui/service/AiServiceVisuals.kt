package com.codexbar.android.ui.service

import androidx.compose.ui.graphics.Color
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService

fun AiService.iconRes(): Int {
    return when (this) {
        AiService.CLAUDE -> R.drawable.ic_service_claude
        AiService.CODEX -> R.drawable.ic_service_codex
        AiService.GEMINI -> R.drawable.ic_service_gemini
    }
}

fun AiService.logoColor(): Color {
    return when (this) {
        AiService.CLAUDE -> Color(0xFFD97757)
        AiService.CODEX -> Color(0xFF10A37F)
        AiService.GEMINI -> Color(0xFF8E75B2)
    }
}
