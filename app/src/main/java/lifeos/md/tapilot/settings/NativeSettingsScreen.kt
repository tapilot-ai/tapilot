package lifeos.md.tapilot.settings

import android.app.Activity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.accessibility.EdgeEffectPreferences
import lifeos.md.tapilot.accessibility.EdgeEffectGeometrySettings
import lifeos.md.tapilot.accessibility.EdgeEffectGeometryMode
import lifeos.md.tapilot.accessibility.FloatingBallPreferences
import lifeos.md.tapilot.bridge.NativeJavascriptBridge
import lifeos.md.tapilot.conversation.ModelProfiles
import lifeos.md.tapilot.conversation.ExecutionMode
import lifeos.md.tapilot.conversation.NativeConversationCoordinator
import lifeos.md.tapilot.theme.ThemePalettes
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import lifeos.md.tapilot.theme.TapilotTheme
import lifeos.md.tapilot.theme.ThemeBackdrop
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONObject

@Composable
fun NativeSettingsScreen(coordinator: NativeConversationCoordinator, activity: Activity, services: NativeJavascriptBridge,
                         onThemeChanged: () -> Unit, onLanguageChanged: () -> Unit) {
    val zh = AppLanguage.resolved(activity) == "zh"
    fun label(a: String, b: String) = if (zh) a else b
    var paletteId by remember { mutableStateOf(AppTheme.paletteId(activity)) }
    var mode by remember { mutableStateOf(AppTheme.preference(activity)) }
    val dark = AppTheme.resolveDark(mode, androidx.compose.foundation.isSystemInDarkTheme())
    var model by remember(coordinator.preset, coordinator.data) { mutableStateOf(coordinator.profile()) }
    var apiKey by remember(coordinator.preset) { mutableStateOf("") }
    var speechKey by remember { mutableStateOf("") }
    var provider by remember(coordinator.data.optString("speechProvider")) { mutableStateOf(coordinator.data.optString("speechProvider", "volcengine")) }
    var picker by remember { mutableStateOf("") }
    var resetModel by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    var floating by remember { mutableStateOf(FloatingBallPreferences.isEnabled(activity)) }
    var edge by remember { mutableStateOf(EdgeEffectPreferences.get(activity)) }
    val busy = coordinator.settingsBusy
    TapilotTheme(dark, paletteId) {
        ThemeBackdrop(dark) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Header(label("设置", "Settings")) { coordinator.back() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Group(label("模型 API", "Model API")) {
                    SettingRow(label("服务商", "Provider"), coordinator.preset) { if (!busy) picker = "model" }
                    listOf("baseUrl" to "API 地址", "modelName" to "模型名称", "modelFamily" to "模型系列").forEach { (field, title) ->
                        val en = when (field) { "baseUrl" -> "API URL"; "modelName" -> "Model name"; else -> "Model family" }
                        OutlinedTextField(value = model.optString(field), onValueChange = { model = JSONObject(model.toString()).put(field, it) },
                            label = { Text(label(title, en)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    }
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") },
                        placeholder = { Text(if (coordinator.hasKey(coordinator.preset)) label("已保存；留空保留", "Saved; leave empty to retain") else label("输入 API Key", "Enter API Key")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactPrimaryAction(label("保存", "Save"), enabled = !busy) { coordinator.saveModel(model, apiKey); apiKey = "" }
                        TextButton(onClick = { coordinator.saveModel(model, apiKey); apiKey = ""; coordinator.testModel() }, enabled = !busy) { Text(label("检查连接", "Check connection")) }
                        TextButton(onClick = { resetModel = true }, enabled = !busy) { Text(label("重置", "Reset")) }
                    }
                    if (coordinator.settingsSection == "model") Status(coordinator.settingsStatus)
                }
                Group(label("语音输入", "Voice input")) {
                    SettingRow(label("转写服务", "Provider"), if (provider == "volcengine") label("火山引擎", "Volcengine") else "Deepgram") { if (!busy) picker = "speech" }
                    Text(if (provider == "volcengine") label("Seed ASR 2.0 小时版 · API 接入", "Seed ASR 2.0 duration · API") else "Deepgram Nova-3 · API", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(speechKey, { speechKey = it }, label = { Text("API Key") },
                        placeholder = { Text(if (coordinator.hasKey("speech-$provider")) label("已保存；留空保留", "Saved; leave empty to retain") else label("输入 API Key", "Enter API Key")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactPrimaryAction(label("保存", "Save"), enabled = !busy) { coordinator.saveSpeech(provider, speechKey); speechKey = "" }
                        TextButton(onClick = { coordinator.saveSpeech(provider, speechKey); speechKey = ""; coordinator.checkSpeech(provider) }, enabled = !busy) { Text(label("检查连接", "Check connection")) }
                    }
                    if (coordinator.settingsSection == "speech") Status(coordinator.settingsStatus)
                }
                Group(label("语音对话与播报", "Voice conversation & playback")) { VoiceOutputSettingsSection(activity, zh, provider) }
                Group(label("语音润色", "Voice polishing")) { SpeechPolishSettingsSection(activity, zh) }
                if (coordinator.settingsSection == "general") Status(coordinator.settingsStatus)
                Group(label("外观与语言", "Appearance & language")) {
                    ThemePalettePicker(paletteId, zh) { id ->
                        AppTheme.setPalette(activity, id); paletteId = id; onThemeChanged()
                    }
                    SettingRow(label("明暗模式", "Appearance"), when (mode) {
                        "light" -> label("浅色", "Light"); "dark" -> label("深色", "Dark"); else -> label("跟随系统", "System")
                    }) { picker = "theme" }
                    SettingRow(label("语言", "Language"), activity.getSharedPreferences("midscene-preferences", 0).getString("language", "system").orEmpty()) { picker = "language" }
                }
                Group(label("全局助手", "Global assistant")) {
                    ToggleRow(label("悬浮球", "Floating assistant"), floating) { value ->
                        services.call("app.setFloatingBallEnabled", JSONObject().put("enabled", value)) { result -> if (result.isSuccess) floating = value }
                    }
                    Text(label("单击圆点展开对话，长按直接语音对答。", "Tap the floating button to expand chat; hold it to start voice chat."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingRow(label("设备控制", "Device control"), label("管理权限", "Manage permissions")) { coordinator.settingsCall("app.openAccessibilitySettings") }
                    SettingRow(label("系统默认助手", "Default assistant"), label("设置", "Set up")) { coordinator.settingsCall("app.requestAssistantRole") }
                    SettingRow(label("后台运行", "Background operation"), label("电池设置", "Battery settings")) { coordinator.settingsCall("app.openBatterySettings") }
                    Text(label("若系统会关闭后台任务，请将 Tapilot 的电池使用设为不受限制。", "If background tasks are interrupted, allow unrestricted battery use for Tapilot."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingRow(label("执行边框", "Execution border"), edge.mode.wireValue) { picker = "edge" }
                    if (edge.mode == EdgeEffectGeometryMode.CUSTOM) {
                        Text(label("圆角 ${edge.cornerRadiusDp.toInt()} dp", "Radius ${edge.cornerRadiusDp.toInt()} dp"), fontSize = 13.sp)
                        Slider(edge.cornerRadiusDp, { edge = edge.copy(cornerRadiusDp = it) }, valueRange = 0f..64f,
                            onValueChangeFinished = { saveEdge(services, edge) })
                        Text(label("内缩 ${edge.insetDp.toInt()} dp", "Inset ${edge.insetDp.toInt()} dp"), fontSize = 13.sp)
                        Slider(edge.insetDp, { edge = edge.copy(insetDp = it) }, valueRange = 0f..24f,
                            onValueChangeFinished = { saveEdge(services, edge) })
                    }
                }
                Group(label("任务与记录", "Tasks & history")) {
                    SettingRow(label("执行模式", "Execution mode"), ExecutionMode.from(coordinator.data).title(zh)) { picker = "execution" }
                    Text(when (ExecutionMode.from(coordinator.data)) {
                        ExecutionMode.YOLO -> label("任务直接执行，不弹出操作确认。", "Tasks run without operation confirmation.")
                        ExecutionMode.CRITICAL -> label("发送、付款、删除等操作前确认。", "Confirm before sending, paying, deleting, and similar operations.")
                        ExecutionMode.ALWAYS -> label("每次调用手机操作前确认。", "Confirm before each phone operation.")
                    }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgentLimitSettings(coordinator, zh)
                    ToggleRow(label("高级 YAML 模式", "Advanced YAML mode"), coordinator.data.optBoolean("advanced")) { coordinator.preference("advanced", it) }
                    SettingRow(label("清空聊天记录", "Clear chat history"), "") { clearHistory = true }
                    SettingRow(label("检查执行引擎", "Check execution engine"), "") { coordinator.settingsCall("runtime.doctor") }
                }
                Group(label("关于 Tapilot", "About Tapilot")) { AppUpdateSettings(activity, zh) }
            }
        }
        }
        if (picker.isNotEmpty()) {
            val choices = when (picker) {
                "model" -> ModelProfiles.ids
                "speech" -> listOf("volcengine", "deepgram")
                "theme" -> listOf("system", "light", "dark")
                "language" -> listOf("system", "zh", "en")
                "execution" -> ExecutionMode.entries.map { it.wireValue }
                else -> listOf("auto", "square", "custom")
            }
            AlertDialog(onDismissRequest = { picker = "" }, title = { Text(label("选择", "Choose")) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { choices.forEach { choice ->
                    val title = when (choice) {
                        "yolo", "critical", "always" -> ExecutionMode.entries.first { it.wireValue == choice }.title(zh)
                        "system" -> label("跟随系统", "System"); "light" -> label("浅色", "Light"); "dark" -> label("深色", "Dark")
                        "zh" -> "简体中文"; "en" -> "English"; "auto" -> label("自动检测", "Automatic")
                        "square" -> label("直角", "Square"); "custom" -> label("自定义", "Custom")
                        "volcengine" -> label("火山引擎", "Volcengine"); "doubao" -> label("豆包", "Doubao"); "qwen" -> label("通义千问", "Qwen"); else -> choice
                    }
                    Text(title, Modifier.fillMaxWidth().clickable {
                        when (picker) {
                            "model" -> { coordinator.selectPreset(choice); apiKey = "" }
                            "speech" -> { provider = choice; speechKey = "" }
                            "theme" -> { AppTheme.set(activity, choice); mode = choice; onThemeChanged() }
                            "language" -> if (AppLanguage.set(activity, choice)) onLanguageChanged()
                            "execution" -> coordinator.preference("executionMode", choice)
                            "edge" -> { edge = edge.copy(mode = EdgeEffectGeometryMode.fromWireValue(choice)); saveEdge(services, edge) }
                        }; picker = ""
                    }.padding(vertical = 14.dp))
                } }
            }, confirmButton = { TextButton(onClick = { picker = "" }) { Text(label("关闭", "Close")) } })
        }
        if (resetModel || clearHistory) AlertDialog(onDismissRequest = { resetModel = false; clearHistory = false },
            title = { Text(if (resetModel) label("重置当前模型？", "Reset this model?") else label("清空聊天记录？", "Clear chat history?")) },
            text = { Text(if (resetModel) label("将删除当前服务商的 API Key 并恢复默认参数。", "Deletes this provider's API Key and restores defaults.") else label("聊天和附件副本将被清除，执行报告仍保留。", "Chat and attachment copies will be removed. Reports are retained.")) },
            confirmButton = { TextButton(onClick = { if (resetModel) coordinator.saveModel(model, reset = true) else coordinator.clearHistory(); resetModel = false; clearHistory = false }) { Text(label("确认", "Confirm")) } },
            dismissButton = { TextButton(onClick = { resetModel = false; clearHistory = false }) { Text(label("取消", "Cancel")) } })
    }
}

private fun saveEdge(services: NativeJavascriptBridge, value: EdgeEffectGeometrySettings) {
    services.call("app.setEdgeEffectSettings", JSONObject().put("mode", value.mode.wireValue).put("cornerRadiusDp", value.cornerRadiusDp).put("insetDp", value.insetDp)) {}
}

@Composable private fun Header(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back) { Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface) }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
@Composable private fun SettingRow(title: String, value: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ToggleRow(title: String, value: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Switch(value, changed) }
}

@Composable fun NativeAdvancedScreen(coordinator: NativeConversationCoordinator) {
    var source by remember { mutableStateOf(coordinator.data.optString("yaml").ifBlank { "cases:\n  - name: Tapilot\n    steps:\n      - aiAct: 打开计算器\n" }) }
    TapilotTheme(coordinator.dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
            Header("YAML") { coordinator.back() }
            OutlinedTextField(source, { source = it; coordinator.preference("yaml", it) }, modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp), label = { Text("YAML") })
            Button(onClick = { coordinator.executeYaml(source) }, enabled = source.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text(if (!coordinator.chinese) "Run" else "执行") }
        }
    }
}

@Composable private fun Status(text: String) {
    if (text.isNotBlank()) Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

/** Compact inline actions retain a 48 dp touch row without filling it with accent color. */
@Composable
private fun CompactPrimaryAction(title: String, enabled: Boolean, click: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Button(onClick = click, enabled = enabled, modifier = Modifier.height(36.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
private fun ThemePalettePicker(selected: String, zh: Boolean, changed: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (zh) "主题" else "Theme", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("classic", "minimal").forEach { id ->
                val light = ThemePalettes.get(id, false)
                val dark = ThemePalettes.get(id, true)
                val active = selected == id
                Surface(modifier = Modifier.weight(1f).selectable(active, role = Role.RadioButton, onClick = { changed(id) }),
                    shape = RoundedCornerShape(16.dp), color = colors.surface,
                    border = BorderStroke(1.dp, if (active) colors.primary else colors.outlineVariant)) {
                    Column(Modifier.heightIn(min = 86.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(light.background, light.primary, dark.background, dark.primary).forEach { color ->
                                Surface(Modifier.size(20.dp), shape = RoundedCornerShape(6.dp), color = Color(color),
                                    border = BorderStroke(.5.dp, colors.outlineVariant)) {}
                            }
                        }
                        val title = if (id == "classic") { if (zh) "经典" else "Classic" } else { if (zh) "素雅" else "Minimal" }
                        Text(title + if (active) " ✓" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text(if (selected == "minimal") { if (zh) "纯白底色，淡蓝点缀。" else "White surfaces with soft blue accents." }
            else { if (zh) "柔和纸色，淡紫点缀。" else "Warm paper tones with lavender accents." },
            fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
    }
}
