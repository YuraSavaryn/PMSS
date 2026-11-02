package com.lab2.ui

import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** "Бульбашковий рівень": кулька зміщується відповідно до нахилу пристрою (з Л.Р.1). */
@Composable
fun TiltIndicator(
    x: Float,
    y: Float,
    modifier: Modifier = Modifier.size(200.dp)
) {
    val g = SensorManager.GRAVITY_EARTH
    val ballColor = Color(0xFF1E88E5)
    val ringColor = Color.Gray

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val ballR = radius * 0.12f

        // Нормалізація: ±g -> ±(radius - ballR)
        val maxShift = radius - ballR
        val dx = (-x / g).coerceIn(-1f, 1f) * maxShift
        val dy = (y / g).coerceIn(-1f, 1f) * maxShift

        drawCircle(ringColor, radius, center, style = Stroke(width = 4f))
        drawLine(ringColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
        drawLine(ringColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)
        drawCircle(ballColor, ballR, Offset(center.x + dx, center.y + dy))
    }
}
