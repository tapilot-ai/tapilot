package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.services.ServiceGateway
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ConversationEffectsTest {
    private fun gateway(call: ((Result<Any?>) -> Unit) -> Unit) = object : ServiceGateway {
        override fun call(method: String, params: JSONObject, sessionId: String, callback: (Result<Any?>) -> Unit) = call(callback)
    }
    @Test fun persistenceFailurePreventsExternalSideEffect() {
        var called = false
        val effects = ConversationEffects(gateway { called = true }, { error("disk full") }, { false })
        assertThrows(IllegalStateException::class.java) { effects.call("device.execute", JSONObject(), "s", AtomicBoolean(), 1) }
        assertFalse(called)
    }
    @Test fun cancellationDuringFlushPreventsDispatch() {
        val stopped = AtomicBoolean(); var called = false
        val effects = ConversationEffects(gateway { called = true }, { stopped.set(true) }, { false })
        assertThrows(IllegalStateException::class.java) { effects.call("device.execute", JSONObject(), "s", stopped, 1) }
        assertFalse(called)
    }
    @Test fun lateSuccessDoesNotOverrideCancellation() {
        val stopped = AtomicBoolean()
        val effects = ConversationEffects(gateway { stopped.set(true); it(Result.success("late")) }, {}, { false })
        assertThrows(IllegalStateException::class.java) { effects.call("device.execute", JSONObject(), "s", stopped, 1) }
    }
    @Test fun successfulCallFollowsDurableSnapshot() {
        val order = mutableListOf<String>()
        val effects = ConversationEffects(gateway { order.add("execute"); it(Result.success("done")) }, { order.add("persist") }, { false })
        assertEquals("done", effects.call("device.execute", JSONObject(), "s", AtomicBoolean(), 1))
        assertEquals(listOf("persist", "execute"), order)
    }
    @Test fun closedOwnerRejectsNewWork() {
        val effects = ConversationEffects(gateway { fail("must not dispatch") }, { fail("must not flush") }, { true })
        assertThrows(IllegalStateException::class.java) { effects.call("device.execute", JSONObject(), "s", AtomicBoolean(), 1) }
    }
}
