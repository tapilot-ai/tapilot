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
