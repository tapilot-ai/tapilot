package lifeos.md.tapilot.chatgpt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/** Settings operations are independent of the conversation worker. All UI state is main-thread owned. */
class ChatGptConnection(private val context: Context, private val changed: () -> Unit) : AutoCloseable {
    val store = ChatGptAccounts(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private var login: ChatGptAccounts.Login? = null
    var accounts by mutableStateOf(store.accounts()); private set
    var activeId by mutableStateOf(store.activeId()); private set
    var model by mutableStateOf(store.selectedModel()); private set
    var models by mutableStateOf(emptyList<ChatGptAccounts.Model>()); private set
    var busy by mutableStateOf(false); private set
    var signingIn by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    val ready: Boolean get() = accounts.any { it.id == activeId && it.signedIn } && model.isNotBlank()
    private fun refreshState() { accounts = store.accounts(); activeId = store.activeId(); model = store.selectedModel(); changed() }
    private fun run(action: () -> String) {
        if (busy || closed) return
        busy = true; status = ""
        executor.execute {
            val result = runCatching(action)
            main.post {
                if (!closed) { busy = false; signingIn = false; login = null; refreshState()
                    status = result.getOrElse { it.message ?: "ChatGPT 连接失败" } }
            }
        }
    }
    private fun loadModels(): List<ChatGptAccounts.Model> {
        val id = store.activeId(); val choices = store.models(id)
        if (choices.none { it.id == store.selectedModel(id) }) store.selectModel(id, choices.first().id)
        return choices
    }
    fun signIn(id: String? = null) {
        if (busy || closed) return
        runCatching {
            val attempt = store.begin(id); login = attempt; signingIn = true
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attempt.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            listen(attempt)
        }.onFailure { login?.discard(); login?.close(); login = null; signingIn = false; status = it.message ?: "无法打开 ChatGPT 登录" }
    }
    private fun listen(attempt: ChatGptAccounts.Login, recovered: Boolean = false) {
        login = attempt; signingIn = true
        run {
            try { attempt.finish() } catch (failure: Exception) {
                if (!closed) attempt.discard()
                if (attempt.cancelled) error("ChatGPT 登录已取消") else throw failure
            } finally { attempt.close() }
            // A catalog failure must not discard a valid completed authorization.
            val catalog = runCatching { loadModels() }
            main.post { if (!closed) models = catalog.getOrDefault(emptyList()) }
            catalog.fold({ "ChatGPT 已连接，请选择模型并开启「使用 ChatGPT」" }, { "ChatGPT 已登录；加载模型失败：${it.message}" })
        }
        status = if (recovered) "已恢复被中断的登录，请返回浏览器刷新授权回调页面。" else "正在等待浏览器授权，请在 15 分钟内完成。"
    }
    fun cancelLogin() { login?.discard(); login?.close(); status = "正在取消登录…" }
    fun refreshModels() = run {
        val catalog = loadModels(); main.post { if (!closed) models = catalog }; "ChatGPT 模型列表已更新"
    }
    fun selectAccount(id: String) {
        if (busy) return
        store.select(id); models = emptyList(); refreshState(); status = ""
        if (store.ready(id)) refreshModels()
    }
    fun selectModel(id: String) {
        if (!busy && models.any { it.id == id }) { store.selectModel(activeId, id); refreshState() }
    }
    fun signOut() = run {
        val revoked = store.signOut(store.activeId())
        main.post { if (!closed) models = emptyList() }
        if (revoked) "ChatGPT 已退出登录" else "已清除本机授权；远端撤销未确认，请在 ChatGPT 设置中断开 Tapilot"
    }
    init {
        runCatching { store.restoreLogin() }.fold(
            onSuccess = { pending ->
                if (pending != null) listen(pending, recovered = true)
                else if (store.loginPhase() == "expired") status = "上次登录已超时，请重新登录，不要刷新旧的授权页面。"
            },
            onFailure = { status = "无法恢复上次登录，请重新登录：${it.message}" },
        )
    }
    override fun close() { closed = true; login?.close(); executor.shutdownNow() }
}
