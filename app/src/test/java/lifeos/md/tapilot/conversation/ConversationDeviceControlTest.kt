package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.accessibility.NativeAction
import lifeos.md.tapilot.device.DeviceCommand
import lifeos.md.tapilot.device.DeviceService
import org.junit.Assert.*
import org.junit.Test

class ConversationDeviceControlTest {
    private val tap = DeviceCommand.Execute(NativeAction.Tap(12f, 34f, null), "frame")

    @Test fun returningToChatBlocksCapturesAndActionsUntilInputFinishes() {
        val calls = mutableListOf<DeviceCommand>()
        val control = ConversationDeviceControl(DeviceService { _, command, complete -> calls.add(command); complete(Result.success(null)) })
        control.pause()
        val ready = control.ready
        for (command in listOf(DeviceCommand.Capture(), tap)) {
            control.execute("run", command) { assertTrue(it.isFailure) }
        }
        assertTrue(calls.isEmpty())
        assertFalse(ready.isDone)
        control.resume()
        assertTrue(ready.isDone)
        control.execute("run", DeviceCommand.Capture()) { assertTrue(it.isSuccess) }
        control.execute("run", tap) { assertTrue(it.isSuccess) }
        assertEquals(listOf(DeviceCommand.Capture(), tap), calls)
    }

    @Test fun modelActionFromBeforeUserInputCannotClickAfterResume() {
        val calls = mutableListOf<DeviceCommand>()
        val control = ConversationDeviceControl(DeviceService { _, command, complete -> calls.add(command); complete(Result.success(null)) })
        control.execute("old", DeviceCommand.Capture()) {}
        control.pause(); control.resume()
        control.execute("old", tap) { assertTrue(it.isFailure) }
        assertEquals(listOf(DeviceCommand.Capture()), calls)
        control.execute("old", DeviceCommand.Capture()) {}
        control.execute("old", tap) { assertTrue(it.isSuccess) }
        assertEquals(tap, calls.last())
    }

    @Test fun captureAlreadyInFlightIsDiscardedWhenUserReturns() {
        var capture: ((Result<Any?>) -> Unit)? = null
        val control = ConversationDeviceControl(DeviceService { _, _, complete -> capture = complete })
        var result: Result<Any?>? = null
        control.execute("run", DeviceCommand.Capture()) { result = it }
        control.pause(); control.resume()
        capture!!(Result.success("old screenshot"))
        assertTrue(result!!.isFailure)
        control.execute("run", tap) { assertTrue(it.isFailure) }
    }

    @Test fun cancellationAndCompletionRemainAvailableWhileInputIsPaused() {
        val calls = mutableListOf<DeviceCommand>()
        val control = ConversationDeviceControl(DeviceService { _, command, complete -> calls.add(command); complete(Result.success(null)) })
        control.pause()
        for (command in listOf(DeviceCommand.Cancel, DeviceCommand.Complete)) {
            control.execute("run", command) { assertTrue(it.isSuccess) }
        }
        assertEquals(listOf(DeviceCommand.Cancel, DeviceCommand.Complete), calls)
        assertTrue(control.paused)
        control.resume()
        control.execute("new-run", DeviceCommand.Execute(NativeAction.Back, null)) { assertTrue(it.isSuccess) }
    }

    @Test fun repeatedPauseKeepsTheWaiterAndNextPauseGetsANewWaiter() {
        val control = ConversationDeviceControl(DeviceService { _, _, complete -> complete(Result.success(null)) })
        control.pause()
        val first = control.ready
        control.pause()
        assertSame(first, control.ready)
        control.resume(); control.pause()
        assertTrue(first.isDone)
        assertFalse(control.ready.isDone)
        assertNotSame(first, control.ready)
    }
}
