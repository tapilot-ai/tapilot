package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.device.DeviceCommand
import lifeos.md.tapilot.device.DeviceService
import java.util.concurrent.CompletableFuture

/** Main-thread device boundary: returning to chat gives the user control until send or leave. */
class ConversationDeviceControl(private val device: DeviceService) : DeviceService {
    private var epoch = 0
    private val sessionEpochs = mutableMapOf<String, Int>()
    @Volatile var ready: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
        private set
    val paused get() = !ready.isDone

    fun pause() {
        if (paused) return
        epoch++
        ready = CompletableFuture()
    }

    fun resume() { ready.complete(Unit) }

    override fun execute(sessionId: String, command: DeviceCommand, complete: (Result<Any?>) -> Unit) {
        if (command == DeviceCommand.Cancel || command == DeviceCommand.Complete) {
            sessionEpochs.remove(sessionId)
            device.execute(sessionId, command, complete)
            return
        }
        val observedEpoch = sessionEpochs.getOrPut(sessionId) { if (paused) epoch - 1 else epoch }
        if (command is DeviceCommand.Capture || command is DeviceCommand.Execute) {
            if (paused) {
                complete(Result.failure(IllegalStateException("Phone interaction paused while the user edits the task in Tapilot. Inspect the current screen after their input.")))
                return
            }
            if (command is DeviceCommand.Execute && observedEpoch != epoch) {
                complete(Result.failure(IllegalStateException("The user changed screens. Capture a fresh screenshot before acting; do not reuse the previous action coordinates.")))
                return
            }
        }
        val captureEpoch = epoch
        device.execute(sessionId, command) callback@ { result ->
            if (command is DeviceCommand.Capture && result.isSuccess) {
                if (paused || captureEpoch != epoch) {
                    complete(Result.failure(IllegalStateException("The user returned to chat during capture. Inspect a fresh screen after their input.")))
                    return@callback
                }
                sessionEpochs[sessionId] = captureEpoch
            }
            complete(result)
        }
    }
}
