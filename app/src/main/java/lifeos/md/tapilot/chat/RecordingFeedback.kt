package lifeos.md.tapilot.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import lifeos.md.tapilot.theme.BrandColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WeChat gesture layout, with Tapilot's established Midscene brand colors. */
@Composable
fun RecordingFeedback(cancel: Boolean, transcribe: Boolean, hint: String, cancelLabel: String, transcribeLabel: String,
    levels: List<Float>, seconds: Int) {
    val danger = MaterialTheme.colorScheme.error
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(BrandColors.recordingOverlay))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = minOf(size.width * .42f, 200.dp.toPx())
            val h = w / 2.1f
            val left = (size.width - w) / 2
            val top = size.height * .45f
            val fill = Brush.horizontalGradient(if (cancel) listOf(danger, danger)
                else listOf(Blue, Color(BrandColors.middle), Purple), startX = left, endX = left + w)
            drawRoundRect(fill, Offset(left, top), Size(w, h), CornerRadius(18.dp.toPx()))
            val notch = Path().apply {
                moveTo(size.width / 2 - 8.dp.toPx(), top + h - 1)
                lineTo(size.width / 2, top + h + 8.dp.toPx())
                lineTo(size.width / 2 + 8.dp.toPx(), top + h - 1)
                close()
            }
            drawPath(notch, fill)
            val step = 3.dp.toPx()
            for (i in 0..23) {
                val height = (3 + 19 * (levels.getOrNull(i) ?: 0f)).dp.toPx()
                drawRoundRect(Color(0xE6FFFFFF), Offset(size.width / 2 + (i - 11.5f) * step, top + (h - height) / 2),
                    Size(2.dp.toPx(), height), CornerRadius(2.dp.toPx()))
            }
            val arc = Path().apply {
                moveTo(-size.width * .25f, size.height * .94f)
                cubicTo(size.width * .2f, size.height * .82f, size.width * .8f, size.height * .82f,
                    size.width * 1.25f, size.height * .94f)
                lineTo(size.width * 1.25f, size.height * 1.2f)
                lineTo(-size.width * .25f, size.height * 1.2f)
                close()
            }
            drawPath(arc, Brush.verticalGradient(listOf(Color(BrandColors.holdStart), Color(BrandColors.holdEnd)),
                startY = size.height * .85f, endY = size.height))
            drawPath(arc, Color(0x66FFFFFF), style = Stroke(4.dp.toPx()))
        }
        Text("%02d:%02d · %s".format(seconds / 60, seconds % 60, hint), Modifier.fillMaxWidth().offset(y = maxHeight * .57f), color = Color(0xB3FFFFFF),
            fontSize = 16.sp, textAlign = TextAlign.Center)
        Box(Modifier.offset(x = maxWidth * -.03f, y = maxHeight * .76f).width(maxWidth * .49f)
            .height(maxHeight * .08f).rotate(-10f)
            .background(Color.White.copy(alpha = if (cancel) .25f else .10f), RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center) {
            Text(cancelLabel, color = Color(0xE0FFFFFF), fontSize = 16.sp)
        }
        Box(Modifier.offset(x = maxWidth * .54f, y = maxHeight * .76f).width(maxWidth * .49f)
            .height(maxHeight * .08f).rotate(10f)
            .background(Color.White.copy(alpha = if (transcribe) .25f else .10f), RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center) {
            Text(transcribeLabel, color = Color(0xE0FFFFFF), fontSize = 16.sp)
        }
    }
}
