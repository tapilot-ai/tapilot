package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureInputGuardTest {
    @Test fun overlappingGesturesDoNotRestoreTouchBeforeAllCallbacksFinish() {
        val changes = mutableListOf<Boolean>()
        val guard = GestureInputGuard { changes.add(it) }
        val first = guard.acquire()
        val second = guard.acquire()
        first.close()
        first.close() // Rejection/cancellation races must not release another gesture.
        assertEquals(listOf(true), changes)
        second.close()
        assertEquals(listOf(true, false), changes)
        guard.acquire().close()
        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test fun aFailedInputUpdateDoesNotLeaveAnAcquiredLease() {
        var fail = true
        val changes = mutableListOf<Boolean>()
        val guard = GestureInputGuard {
            if (fail) throw IllegalStateException("Window unavailable")
            changes.add(it)
        }
        assertThrows(IllegalStateException::class.java) { guard.acquire() }
        fail = false
        guard.acquire().close()
        assertEquals(listOf(true, false), changes)
    }
}
