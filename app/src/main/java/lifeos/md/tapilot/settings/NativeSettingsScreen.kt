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
import lifeos.md.tapilot.conversation.NativeConversationCoordinator
import lifeos.md.tapilot.theme.BrandColors
import org.json.JSONObject

@Composable
private fun NativeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(primary = Color(BrandColors.darkPrimary), background = Color(BrandColors.darkBackground),
        surface = Color(BrandColors.darkSurface), onSurface = Color(BrandColors.darkText), onBackground = Color(BrandColors.darkText),
        onSurfaceVariant = Color(BrandColors.darkMuted), outline = Color(BrandColors.darkLine))
        else lightColorScheme(primary = Color(BrandColors.lightPrimary), background = Color(BrandColors.lightBackground),
            surface = Color.White, onSurface = Color(BrandColors.lightText), onBackground = Color(BrandColors.lightText),
            onSurfaceVariant = Color(BrandColors.lightMuted), outline = Color(BrandColors.lightLine))
    MaterialTheme(colorScheme = colors) { content() }
}

@Composable
fun NativeSettingsScreen(coordinator: NativeConversationCoordinator, activity: Activity, services: NativeJavascriptBridge,
                         onThemeChanged: () -> Unit, onLanguageChanged: () -> Unit) {
    val zh = AppLanguage.resolved(activity) == "zh"
    fun label(a: String, b: String) = if (zh) a else b
    var dark by remember { mutableStateOf(AppTheme.isDark(activity)) }
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
    NativeTheme(dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
            Header(label("设置", "Settings")) { coordinator.back() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Group(label("模型 API", "Model API")) {
                    SettingRow(label("服务商", "Provider"), coordinator.preset) { if (!busy) picker = "model" }
                    listOf("baseUrl" to "API 地址", "modelName" to "模型名称", "modelFamily" to "模型系列").forEach { (field, title) ->
                        val en = when (field) { "baseUrl" -> "API URL"; "modelName" -> "Model name"; else -> "Model family" }
                        OutlinedTextField(value = model.optString(field), onValueChange = { model = JSONObject(model.toString()).put(field, it) },
                            label = { Text(label(title, en)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") },
                        placeholder = { Text(if (coordinator.hasKey(coordinator.preset)) label("已保存；留空保留", "Saved; leave empty to retain") else label("输入 API Key", "Enter API Key")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { coordinator.saveModel(model, apiKey); apiKey = "" }, enabled = !busy) { Text(label("保存", "Save")) }
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
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { coordinator.saveSpeech(provider, speechKey); speechKey = "" }, enabled = !busy) { Text(label("保存", "Save")) }
                        TextButton(onClick = { coordinator.saveSpeech(provider, speechKey); speechKey = ""; coordinator.checkSpeech(provider) }, enabled = !busy) { Text(label("检查连接", "Check connection")) }
                    }
                    if (coordinator.settingsSection == "speech") Status(coordinator.settingsStatus)
                }
                if (coordinator.settingsSection == "general") Status(coordinator.settingsStatus)
                Group(label("外观与语言", "Appearance & language")) {
                    SettingRow(label("主题", "Theme"), AppTheme.preference(activity)) { picker = "theme" }
                    SettingRow(label("语言", "Language"), activity.getSharedPreferences("midscene-preferences", 0).getString("language", "system").orEmpty()) { picker = "language" }
                }
                Group(label("全局助手", "Global assistant")) {
                    ToggleRow(label("悬浮球", "Floating assistant"), floating) { value ->
                        services.call("app.setFloatingBallEnabled", JSONObject().put("enabled", value)) { result -> if (result.isSuccess) floating = value }
                    }
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
                    ToggleRow(label("每次操作前确认", "Confirm each operation"), coordinator.data.optBoolean("confirmEveryStep")) { coordinator.preference("confirmEveryStep", it) }
                    ToggleRow(label("高级 YAML 模式", "Advanced YAML mode"), coordinator.data.optBoolean("advanced")) { coordinator.preference("advanced", it) }
                    SettingRow(label("清空聊天记录", "Clear chat history"), "") { clearHistory = true }
                    SettingRow(label("检查执行引擎", "Check execution engine"), "") { coordinator.settingsCall("runtime.doctor") }
                }
                val version = remember { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty() }
                Text("Tapilot $version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (picker.isNotEmpty()) {
            val choices = when (picker) {
                "model" -> ModelProfiles.ids
                "speech" -> listOf("volcengine", "deepgram")
                "theme" -> listOf("system", "light", "dark")
                "language" -> listOf("system", "zh", "en")
                else -> listOf("auto", "square", "custom")
            }
            AlertDialog(onDismissRequest = { picker = "" }, title = { Text(label("选择", "Choose")) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { choices.forEach { choice ->
                    val title = when (choice) {
                        "system" -> label("跟随系统", "System"); "light" -> label("浅色", "Light"); "dark" -> label("深色", "Dark")
                        "zh" -> "简体中文"; "en" -> "English"; "auto" -> label("自动检测", "Automatic")
                        "square" -> label("直角", "Square"); "custom" -> label("自定义", "Custom")
                        "volcengine" -> label("火山引擎", "Volcengine"); "doubao" -> label("豆包", "Doubao"); "qwen" -> label("通义千问", "Qwen"); else -> choice
                    }
                    Text(title, Modifier.fillMaxWidth().clickable {
                        when (picker) {
                            "model" -> { coordinator.selectPreset(choice); apiKey = "" }
                            "speech" -> { provider = choice; speechKey = "" }
                            "theme" -> { AppTheme.set(activity, choice); dark = AppTheme.isDark(activity); onThemeChanged() }
                            "language" -> if (AppLanguage.set(activity, choice)) onLanguageChanged()
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
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back) { Text("‹", fontSize = 26.sp) }
        Text(title, fontSize = 18.sp)
    }
}
@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
@Composable private fun SettingRow(title: String, value: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ToggleRow(title: String, value: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 14.sp); Switch(value, changed) }
}

@Composable fun NativeAdvancedScreen(coordinator: NativeConversationCoordinator) {
    var source by remember { mutableStateOf(coordinator.data.optString("yaml").ifBlank { "cases:\n  - name: Tapilot\n    steps:\n      - aiAct: 打开计算器\n" }) }
    NativeTheme(coordinator.dark) {
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
