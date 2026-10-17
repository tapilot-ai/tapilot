package lifeos.md.tapilot.conversation

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ConversationWriterTest {
    @Test fun snapshotsDetachMutableHistoryAndDraftDoesNotRewriteHistory() {
        val directory = Files.createTempDirectory("tapilot-writer").toFile()
        val store = ConversationStore(directory.resolve("state.json"))
        val writer = ConversationWriter(store) { throw AssertionError(it) }
        try {
            val entry = JSONObject().put("text", "original")
            writer.save(ConversationStore.empty().put("history", JSONArray().put(entry)))
            entry.put("text", "mutated")
            writer.flush()
            val bytes = directory.resolve("state.json").readBytes()
            writer.saveDraft("draft only"); writer.flush()
            assertArrayEquals(bytes, directory.resolve("state.json").readBytes())
            assertEquals("draft only", store.read().getString("draft"))
            assertEquals("original", store.read().getJSONArray("history").getJSONObject(0).getString("text"))
            val previousDraft = directory.resolve("state.json.draft").readBytes()
            writer.save(ConversationStore.empty().put("draft", "sent")); writer.flush()
            directory.resolve("state.json.draft").writeBytes(previousDraft)
            assertEquals("sent", store.read().getString("draft"))
        } finally { writer.close(); directory.deleteRecursively() }
    }
    @Test fun cachedHistoryEncodingTracksNestedChangesOrderRemovalAndDuplicateIds() {
        val directory = Files.createTempDirectory("tapilot-writer-cache").toFile()
        val store = ConversationStore(directory.resolve("state.json"))
        val writer = ConversationWriter(store) { throw AssertionError(it) }
        try {
            val first = JSONObject().put("id", "duplicate").put("nested", JSONArray().put(JSONObject.NULL).put("old"))
            val second = JSONObject().put("id", "duplicate").put("text", "中文\\\"\n")
            val state = ConversationStore.empty().put("history", JSONArray().put(first).put(second))
            writer.save(state); writer.flush()
            first.getJSONArray("nested").put(1, "new")
            state.put("history", JSONArray().put(second).put(first)).put("draft", "updated")
            writer.save(state); writer.flush()
            val saved = store.read()
            assertEquals("updated", saved.getString("draft"))
            assertEquals(second.getString("text"), saved.getJSONArray("history").getJSONObject(0).getString("text"))
            assertEquals("new", saved.getJSONArray("history").getJSONObject(1).getJSONArray("nested").getString(1))
            assertTrue(saved.getJSONArray("history").getJSONObject(1).getJSONArray("nested").isNull(0))
            state.put("history", JSONArray())
            writer.save(state); writer.flush()
            assertEquals(0, store.read().getJSONArray("history").length())
        } finally { writer.close(); directory.deleteRecursively() }
    }
    @Test fun diagnosticFailuresDoNotPreventDurableWrites() {
        val directory = Files.createTempDirectory("tapilot-writer-diagnostics").toFile()
        val store = ConversationStore(directory.resolve("state.json"))
        val writer = ConversationWriter(store, onTiming = { _, _ -> error("diagnostic failure") }) { throw AssertionError(it) }
        try {
            writer.save(ConversationStore.empty().put("draft", "kept")); writer.flush()
            assertEquals("kept", store.read().getString("draft"))
        } finally { writer.close(); directory.deleteRecursively() }
    }
    @Test fun ioFailureIsReportedOffCallerThreadAndBlocksTheNextOperation() {
        val directory = Files.createTempDirectory("tapilot-writer-failure").toFile()
        val blocker = directory.resolve("not-a-directory").apply { writeText("file") }
        val caller = Thread.currentThread()
        val notified = CountDownLatch(1)
        var callbackThread: Thread? = null
        val writer = ConversationWriter(ConversationStore(blocker.resolve("state.json"))) {
            callbackThread = Thread.currentThread(); notified.countDown()
        }
        try {
            writer.save(ConversationStore.empty())
            assertTrue(notified.await(2, TimeUnit.SECONDS))
            assertNotSame(caller, callbackThread)
            assertThrows(IllegalStateException::class.java) { writer.flush() }
        } finally { writer.close(); directory.deleteRecursively() }
    }
}
