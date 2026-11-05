package lifeos.md.tapilot.conversation

import org.junit.Assert.*
import org.junit.Test

class BackgroundScreenSetupIntentTest {
    @Test fun acceptsExplicitSetupRequestsInBothLanguages() {
        listOf("帮我开启后台屏幕", "打开一下虚拟屏", "请帮我配置 Tapilot 的后台屏幕", "把后台屏幕打开",
            "恢复后台屏幕的连接", "Can you enable the background screen?", "Set up Tapilot virtual display",
            "Please configure my background screen", "帮我开启后台屏幕，告诉我怎么用").forEach { assertTrue(it, BackgroundScreenSetupIntent.matches(it)) }
    }
    @Test fun ordinaryBackgroundTasksNeverAuthorizeConfiguration() {
        listOf("在后台屏幕打开系统设置", "打开设置并进入应用页，使用后台屏幕", "后台屏幕里打开计算器",
            "Open Android Settings on the background screen. Navigate to Apps.",
            "Read Settings using the virtual display", "后台屏幕", "继续").forEach { assertFalse(it, BackgroundScreenSetupIntent.matches(it)) }
    }
    @Test fun featureQuestionsDoNotAuthorizeChanges() {
        listOf("如何开启后台屏幕", "怎么配置虚拟屏", "是否支持开启后台屏幕", "How do I enable the background screen?",
            "Please explain how to configure the virtual display").forEach { assertFalse(it, BackgroundScreenSetupIntent.matches(it)) }
    }
}
