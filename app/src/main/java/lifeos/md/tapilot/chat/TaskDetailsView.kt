package lifeos.md.tapilot.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TaskDetailsView(task: ChatTask, zh: Boolean) {
    val duration = task.durationMillis?.let { taskDurationLabel(it, zh) }
    val hasProgress = task.plan.isNotEmpty() || task.phoneOperations > 0 || task.phase == "waiting_user"
    if (!hasProgress && duration == null) return
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val current = task.plan.getOrNull(task.currentStep)
    val caption = when (task.phase) {
        "waiting_user" -> if (zh) "等待你补充信息" else "Waiting for your answer"
        "paused" -> if (zh) "进度已保存" else "Progress saved"
        "failed" -> if (zh) "任务未完成，可重试" else "Task incomplete; retry available"
        "completed" -> if (zh) "任务已完成" else "Task completed"
        else -> current ?: if (zh) "正在执行任务" else "Working on task"
    }
    Spacer(Modifier.height(6.dp))
    Text(listOfNotNull(caption, duration).joinToString(" · "), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (hasProgress) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
        Text(if (zh) "${task.phoneOperations} 次操作 · ${if (expanded) "收起进度" else "查看进度"}"
            else "${task.phoneOperations} operations · ${if (expanded) "Hide progress" else "Show progress"}", fontSize = 12.sp)
    }
    if (expanded) {
        task.plan.forEachIndexed { index, step ->
            // Only explicit plan progress, never infer success from attempted operations.
            val marker = if (index < task.currentStep) "✓" else if (index == task.currentStep && task.phase == "running") "›" else "·"
            Text("$marker $step", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
        }
        task.operations.forEach { (instruction, success, result) ->
            Text("${if (success) "✓" else "!"} $instruction", fontSize = 12.sp, color = if (success) colors.onSurfaceVariant else colors.error,
                modifier = Modifier.padding(vertical = 3.dp))
            if (!success && result.isNotBlank()) Text(result, fontSize = 12.sp, color = colors.error, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        Text(if (zh) "第 ${task.attempt} 次尝试 · ${task.modelTurns} 轮对话" else "Attempt ${task.attempt} · ${task.modelTurns} model turns",
            fontSize = 11.sp, color = colors.onSurfaceVariant)
    }
}
