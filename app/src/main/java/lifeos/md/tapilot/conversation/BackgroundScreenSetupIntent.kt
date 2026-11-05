package lifeos.md.tapilot.conversation

/** Authorization comes from the user's request, never from an attachment or a model tool choice. */
internal object BackgroundScreenSetupIntent {
    private val objectName = "(?:后台|虚拟)屏(?:幕)?"
    private val chinese = Regex("(?:打开|开启|启用|配置|设置|修复|恢复|连接)\\s*(?:一下|下)?\\s*(?:Tapilot\\s*的?\\s*)?$objectName", RegexOption.IGNORE_CASE)
    private val reversed = Regex("(?:把|将)\\s*(?:Tapilot\\s*的?\\s*)?$objectName\\s*(?:打开|开启|启用|配置好|设置好|恢复)(?=[，。！？,!.?\\s]|$)", RegexOption.IGNORE_CASE)
    private val english = Regex("\\b(?:set\\s*up|enable|configure|turn\\s+on|open|repair|restore|reconnect)\\s+(?:(?:the|a|my|Tapilot(?:'s)?)\\s+)*(?:background|virtual)\\s+(?:screen|display)\\b", RegexOption.IGNORE_CASE)
    private val informational = Regex("(?:如何|怎么|怎样|教程|原理|是否支持|\\bhow\\b|\\bexplain\\b)", RegexOption.IGNORE_CASE)
    fun matches(request: String): Boolean {
        val action = listOf(chinese, reversed, english).mapNotNull { it.find(request) }.minByOrNull { it.range.first }
            ?: return false
        val question = informational.find(request)
        return question == null || question.range.first > action.range.first
    }

}
