package com.intentgate.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.intentgate.app.R

object GateColors {
    val Ink = Color(0xFF073F31)
    val Secondary = Color(0xFF536E67)
    val Muted = Color(0xFF7B968A)
    val Ring = Color(0xFF23754F)
    val Track = Color(0xFFD0E4D1)
    val Mint = Color(0xFF83DFB5)
    val Surface = Color(0xFCFFFFFF)
    val Background = listOf(Color(0xFFE0F5E5), Color(0xFFEDF7DD), Color(0xFFF6F9EE))
}
val GateFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)
