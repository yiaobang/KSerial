package com.yiaobang.kserial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun SerialChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF66BB6A),
    gridColor: Color = Color.Gray.copy(alpha = 0.2f)
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0D1117))
            .border(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val width = size.width
            val height = size.height
            
            // 绘制网格
            val gridStep = 40.dp.toPx()
            var x = 0f
            while (x < width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, height))
                x += gridStep
            }
            var y = 0f
            while (y < height) {
                drawLine(gridColor, Offset(0f, y), Offset(width, y))
                y += gridStep
            }

            if (dataPoints.size < 2) return@Canvas

            // 绘制数据曲线
            val maxData = dataPoints.maxOrNull() ?: 1f
            val minData = dataPoints.minOrNull() ?: 0f
            val range = (maxData - minData).coerceAtLeast(1f)
            
            val stepX = width / (dataPoints.size - 1)
            val path = Path()

            dataPoints.forEachIndexed { index, value ->
                val px = index * stepX
                val py = height - ((value - minData) / range * height)
                if (index == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
