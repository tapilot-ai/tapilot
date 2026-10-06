package lifeos.md.tapilot.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import lifeos.md.tapilot.theme.BrandColors

/** One speech surface for chat and the global assistant, retaining Tapilot's brand colors. */
@Composable
fun RecordingFeedback(cancel: Boolean, send: Boolean, hint: String, cancelLabel: String, sendLabel: String,
    levels: List<Float>, seconds: Int, transcript: String = "", transcribing: Boolean = false,
    visible: Boolean = true, onCancel: () -> Unit, onSend: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(transcript) { scroll.scrollTo(scroll.maxValue) }
    val danger = MaterialTheme.colorScheme.error
    val accent = MaterialTheme.colorScheme.primary
    val bubbleScale by animateFloatAsState(if (transcribing) .97f else 1f, tween(160), label = "speech-phase")
    val cancelScale by animateFloatAsState(if (cancel && visible) 1.05f else 1f, tween(120), label = "speech-cancel")
    val sendScale by animateFloatAsState(if (send && visible) 1.05f else 1f, tween(120), label = "speech-send")
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(BrandColors.recordingOverlay))) {
        val layout = RecordingLayout(maxWidth.value, maxHeight.value)
        Canvas(Modifier.fillMaxSize()) {
            val arc = Path().apply {
                val top = layout.arcTop.dp.toPx()
                moveTo(-size.width * .8f, size.height)
                quadraticTo(size.width / 2, top - 100.dp.toPx(), size.width * 1.8f, size.height)
                lineTo(size.width * 1.8f, size.height * 2)
                lineTo(-size.width * .8f, size.height * 2)
                close()
            }
            drawPath(arc, Brush.verticalGradient(listOf(Color(BrandColors.holdStart), Color(BrandColors.holdEnd))))
            drawPath(arc, Color(0x66FFFFFF), style = Stroke(4.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().offset(y = layout.bubbleTop.dp).height(layout.bubbleHeight.dp)
            .animateEnterExit(enter = scaleIn(tween(200), initialScale = .92f), exit = scaleOut(tween(160), targetScale = .92f))
            .graphicsLayer { scaleX = bubbleScale; scaleY = bubbleScale },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.widthIn(min = minOf(166f, layout.width * .44f).dp, max = (layout.width * .88f).dp)
                .heightIn(min = 80.dp, max = minOf(layout.height * .3f, layout.bubbleHeight - 64f).coerceAtLeast(80f).dp)
                .background(if (cancel) danger else accent, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center) {
                if (transcript.isNotEmpty()) Text(transcript, Modifier.padding(18.dp).verticalScroll(scroll),
                    color = Color.White, fontSize = 18.sp, lineHeight = 27.sp)
                else Canvas(Modifier.width(104.dp).height(80.dp)) {
                    val step = 3.dp.toPx()
                    for (i in 0..23) {
                        val h = (3 + 19 * (levels.getOrNull(i) ?: 0f)).dp.toPx()
                        drawRoundRect(Color(0xE6FFFFFF), Offset(size.width / 2 + (i - 11.5f) * step, (size.height - h) / 2),
                            Size(2.dp.toPx(), h), CornerRadius(2.dp.toPx()))
                    }
                }
            }
            Canvas(Modifier.width(16.dp).height(8.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() },
                    if (cancel) danger else accent)
            }
            Text(hint, Modifier.padding(top = 16.dp), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text("%02d:%02d".format(seconds / 60, seconds % 60), Modifier.padding(top = 8.dp),
                color = Color(0xB3FFFFFF), fontSize = 12.sp)
        }
        TextButton(onClick = onCancel, enabled = visible, modifier = Modifier.offset(x = layout.cancelLeft.dp, y = layout.buttonTop.dp)
            .width(layout.buttonWidth.dp).height(layout.buttonHeight.dp).rotate(-10f)
            .animateEnterExit(enter = slideInVertically(tween(200)) { it / 4 }, exit = slideOutVertically(tween(160)) { it / 4 })
            .graphicsLayer { scaleX = cancelScale; scaleY = cancelScale }
            .border(if (cancel) 2.dp else 0.dp, if (cancel) Color.White else Color.Transparent, RoundedCornerShape(60.dp))
            .background(if (cancel) danger else Color.White.copy(alpha = .12f), RoundedCornerShape(60.dp))) {
            Text(cancelLabel, color = Color.White, fontSize = 16.sp)
        }
        TextButton(onClick = onSend, enabled = visible && !transcribing,
            modifier = Modifier.offset(x = layout.sendLeft.dp, y = layout.buttonTop.dp)
                .width(layout.buttonWidth.dp).height(layout.buttonHeight.dp).rotate(10f)
                .animateEnterExit(enter = slideInVertically(tween(200)) { it / 4 }, exit = slideOutVertically(tween(160)) { it / 4 })
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .border(if (send) 2.dp else 0.dp, if (send) Color.White else Color.Transparent, RoundedCornerShape(60.dp))
                .background(accent, RoundedCornerShape(60.dp))) {
            Text(sendLabel, color = Color.White.copy(alpha = if (transcribing) .45f else 1f), fontSize = 16.sp)
        }
    }
    }
}
