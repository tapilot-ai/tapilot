package lifeos.md.tapilot.conversation

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class TaskExecutionTest {
    @Test fun replacementIgnoresLateCompletionAndKeepsOldCancellation() {
        val task = TaskExecution()
        val old = task.begin("first", "s1")
        val barrier = task.requestStop()
        assertSame(barrier, task.requestStop())
        assertTrue(old.get())
        assertFalse(barrier.isDone)
        barrier.complete(true)
        assertTrue(task.finish("s1"))
        val replacement = task.begin("second", "s2")
        assertFalse(task.finish("s1"))
        assertTrue(task.running)
        assertEquals("second", task.taskId)
        assertTrue(old.get())
        assertFalse(replacement.get())
        assertNull(task.stopBarrier)
    }
    @Test fun rejectsOverlappingTasks() {
        val task = TaskExecution(); task.begin("first", "s1")
        assertThrows(IllegalStateException::class.java) { task.begin("second", "s2") }
    }
    @Test fun queuedInputsRoundTripWithoutSharingMutableAttachments() {
        val input = JSONObject("""{"text":"继续","global":true,"continueOf":"task-1","attachment":{"id":"file-1"}}""")
        val turn = PendingTurn.from(input)
        input.getJSONObject("attachment").put("id", "changed")
        val restored = PendingTurn.from(turn.json())
        assertEquals("继续", restored.text)
        assertTrue(restored.global)
        assertEquals("task-1", restored.continueOf)
        assertEquals("file-1", restored.attachment!!.getString("id"))
    }
}
