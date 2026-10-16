package lifeos.md.tapilot.chatgpt

import lifeos.md.tapilot.AppText.text as appText
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
    val store = ChatGptAccounts.get(context)
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
                    status = result.getOrElse { it.message ?: appText("ChatGPT 连接失败", "ChatGPT connection failed") } }
            }
        }
    }
    private fun loadModels(): List<ChatGptAccounts.Model> {
        val id = store.activeId(); val choices = store.models(id)
        val selected = ChatGptModelSelection.select(choices.map { it.id }, store.selectedModel(id))
        if (selected != store.selectedModel(id)) store.selectModel(id, selected)
        return choices
    }
    fun signIn(id: String? = null) {
        if (busy || closed) return
        runCatching {
            val attempt = store.begin(id); login = attempt; signingIn = true
            listen(attempt, openBrowser = true)
        }.onFailure { login?.discard(); login?.close(); login = null; signingIn = false; status = it.message ?: appText("无法打开 ChatGPT 登录", "Unable to open ChatGPT sign-in") }
    }
    private fun listen(attempt: ChatGptAccounts.Login, recovered: Boolean = false, openBrowser: Boolean = false) {
        login = attempt; signingIn = true
        try { ChatGptLoginService.start(context) } catch (failure: Exception) {
            attempt.close(); login = null; signingIn = false
            status = appText("无法保持登录回调运行，请返回前台重试：${failure.message}", "Unable to keep sign-in running. Return to the app and retry: ${failure.message}")
            return
        }
        run {
            try { attempt.finish {
                if (openBrowser) main.post {
                    if (!closed && signingIn) runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attempt.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { attempt.discard(); attempt.close(); status = appText("无法打开 ChatGPT 登录：${it.message}", "Unable to open ChatGPT sign-in: ${it.message}") }
                }
            } } catch (failure: Exception) {
                if (!closed) attempt.discard()
                if (attempt.cancelled) error(appText("ChatGPT 登录已取消", "ChatGPT sign-in cancelled")) else throw failure
            } finally { attempt.close(); ChatGptLoginService.stop(context) }
            val polishing = runCatching { lifeos.md.tapilot.speech.SpeechPolishStore(context).enableForSiwcIfUnset() }
            // A catalog failure must not discard a valid completed authorization.
            val catalog = runCatching { loadModels() }
            main.post { if (!closed) models = catalog.getOrDefault(emptyList()) }
            catalog.fold({ if (polishing.getOrDefault(false)) appText("ChatGPT 已连接，自动润色已开启，模型已就绪", "ChatGPT connected; automatic polishing enabled. Model ready") else if (polishing.isFailure) appText("ChatGPT 已连接；自动润色设置失败，请在语音润色中检查", "ChatGPT connected; polishing setup failed. Check Voice polishing settings") else appText("ChatGPT 已连接，模型已就绪", "ChatGPT connected. Model ready") }, { appText("ChatGPT 已登录；加载模型失败：${it.message}", "Signed in to ChatGPT; model loading failed: ${it.message}") })
        }
        status = if (recovered) appText("已恢复被中断的登录，请返回浏览器刷新授权回调页面。", "Interrupted sign-in restored. Return to your browser and refresh the authorization callback page.") else appText("正在等待浏览器授权，请在 15 分钟内完成。", "Waiting for browser authorization. Complete it within 15 minutes.")
    }
    fun cancelLogin() { login?.discard(); login?.close(); status = appText("正在取消登录…", "Cancelling sign-in…") }
    fun refreshModels() = run {
        val catalog = loadModels(); main.post { if (!closed) models = catalog }; appText("ChatGPT 模型列表已更新", "ChatGPT model list updated")
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
        if (revoked) appText("ChatGPT 已退出登录", "Signed out of ChatGPT") else appText("已清除本机授权；远端撤销未确认，请在 ChatGPT 设置中断开 Tapilot", "Local credentials cleared; remote revocation unconfirmed. Disconnect Tapilot in ChatGPT settings")
    }
    init {
        runCatching { store.restoreLogin() }.fold(
            onSuccess = { pending ->
                if (pending != null) listen(pending, recovered = true)
                else if (store.loginPhase() == "expired") status = appText("上次登录已超时，请重新登录，不要刷新旧的授权页面。", "Previous sign-in timed out. Sign in again; do not refresh the old authorization page.")
            },
            onFailure = { status = appText("无法恢复上次登录，请重新登录：${it.message}", "Unable to restore sign-in. Sign in again: ${it.message}") },
        )
    }
    override fun close() { closed = true; login?.close(); ChatGptLoginService.stop(context); executor.shutdownNow() }
}
