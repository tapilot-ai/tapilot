package lifeos.md.tapilot.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Matches runtime/style.css .voice-state.is-recording during the native UI migration. */
@Composable
fun RecordingFeedback(cancel: Boolean, title: String, hint: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val pulse by rememberInfiniteTransition(label = "voice-pulse").animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "voice-ring")
    val background = if (cancel) Brush.linearGradient(listOf(Color(0xFFC43E62), Color(0xFFC43E62)))
        else Brush.linearGradient(listOf(Color(0xFF3669E9), Color(0xFF7B49DD)))
    Box(modifier.shadow(18.dp, shape, ambientColor = Color(0x6618245B), spotColor = Color(0x6618245B))
        .background(background, shape).border(1.dp, Color(0x44FFFFFF), shape)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize((44 + pulse * 2).dp).background(Color(0x16FFFFFF), CircleShape))
            Box(Modifier.size(44.dp).background(Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                Text("🎙", fontSize = 25.sp)
            }
        }
        Text("$title\n$hint", Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 66.dp, bottom = 24.dp),
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 25.5.sp, textAlign = TextAlign.Center)
    }
}
