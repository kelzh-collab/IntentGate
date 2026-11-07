package com.intentgate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

enum class GateIcon { Home, Apps, Stats, Settings, Chevron, Back, Logo, Search, Plus }
@Composable
fun GateGlyph(icon: GateIcon, modifier: Modifier, color: Color = GateColors.Ink, filled: Boolean = false) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val w = 1.65f * u
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*u,y*u), Offset(x2*u,y2*u), w, StrokeCap.Round)
        when (icon) {
            GateIcon.Logo -> { line(3f,2f,3f,22f); line(21f,2f,21f,22f); drawCircle(color, 4.7f*u, Offset(12f*u,12f*u)) }
            GateIcon.Home -> {
                val p = Path().apply { moveTo(3f*u,10f*u); lineTo(12f*u,2.5f*u); lineTo(21f*u,10f*u); lineTo(21f*u,21f*u); lineTo(15f*u,21f*u); lineTo(15f*u,15f*u); lineTo(9f*u,15f*u); lineTo(9f*u,21f*u); lineTo(3f*u,21f*u); close() }
                if (filled) drawPath(p,color) else drawPath(p,color,style=Stroke(w, cap=StrokeCap.Round))
            }
            GateIcon.Apps -> for (x in listOf(3f,14f)) for (y in listOf(3f,14f)) {
                if (filled) drawRoundRect(color, Offset(x*u,y*u),Size(7f*u,7f*u), CornerRadius(1.8f*u))
                else drawRoundRect(color, Offset(x*u,y*u),Size(7f*u,7f*u), CornerRadius(1.8f*u),style=Stroke(w))
            }
            GateIcon.Stats -> { line(5f,12f,5f,21f); line(12f,3f,12f,21f); line(19f,8f,19f,21f) }
            GateIcon.Settings -> {
                line(3f,5f,21f,5f); line(3f,12f,21f,12f); line(3f,19f,21f,19f)
                for ((x,y) in listOf(15f to 5f,8f to 12f,15f to 19f)) {
                    drawCircle(GateColors.Background.last(),2.2f*u,Offset(x*u,y*u)); drawCircle(color,2.2f*u,Offset(x*u,y*u),style=Stroke(w))
                }
            }
            GateIcon.Chevron -> { line(9f,5f,16f,12f); line(16f,12f,9f,19f) }
            GateIcon.Search -> { drawCircle(color, 6.5f*u, Offset(10.5f*u,10.5f*u), style=Stroke(w)); line(15.5f,15.5f,20.5f,20.5f) }
            GateIcon.Plus -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
            GateIcon.Back -> { line(15f,5f,8f,12f); line(8f,12f,15f,19f) }
        }
    }
}
