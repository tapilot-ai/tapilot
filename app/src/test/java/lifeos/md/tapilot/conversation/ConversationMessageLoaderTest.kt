package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.services.ServiceGateway
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConversationMessageLoaderTest {
    private class Queue : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.add(command) }
        fun next() = jobs.removeFirst().run()
    }
    private class Fixture {
        val worker = Queue()
        val main = Queue()
        val loader = ConversationMessageLoader(worker) { main.execute(it) }
        val results = mutableListOf<String>()
        fun load(text: String, action: MessageLoadAction = MessageLoadAction.PREVIEW) = loader.load(action,
            { JSONObject().put("text", text) }, { results.add(it.getOrThrow()!!.getString("text")) })
    }

    @Test fun newerRequestWinsEvenWhenTheOldResultHasAlreadyReachedTheMainQueue() {
        val f = Fixture()
        f.load("old"); f.worker.next()
        f.load("new"); f.worker.next()
        f.main.next(); f.main.next()
        assertEquals(listOf("new"), f.results)
    }

    @Test fun leavingAndReturningToAConversationCannotReviveAnOldRetry() {
        val f = Fixture()
        f.load("retry", MessageLoadAction.RETRY); f.worker.next()
        f.loader.discard() // Navigation invalidates the request even if the same conversation is selected again.
        f.main.next()
        assertTrue(f.results.isEmpty())
    }

    @Test fun typingAfterRequestingEditPreservesTheNewDraft() {
        val f = Fixture()
        f.load("old draft", MessageLoadAction.EDIT); f.worker.next()
        f.loader.discard(MessageLoadAction.EDIT)
        f.main.next()
        assertTrue(f.results.isEmpty())
    }

    @Test fun typingAnUnrelatedDraftDoesNotCancelAnExplicitRetry() {
        val f = Fixture()
        f.load("original task", MessageLoadAction.RETRY); f.worker.next()
        f.loader.discard(MessageLoadAction.EDIT)
        f.main.next()
        assertEquals(listOf("original task"), f.results)
    }

    @Test fun closedPreviewDoesNotEvenReadIfTheWorkerHasNotStartedYet() {
        val f = Fixture()
        var reads = 0
        f.loader.load(MessageLoadAction.PREVIEW, { reads++; null }, { fail("Canceled preview published") })
        f.loader.discard(MessageLoadAction.PREVIEW)
        f.worker.next()
        assertEquals(0, reads)
        assertTrue(f.main.jobs.isEmpty())
    }

    @Test fun obsoleteFailureDoesNotReplaceCurrentFeedbackButCurrentFailureIsReported() {
        val f = Fixture()
        val failures = mutableListOf<String?>()
        f.loader.load(MessageLoadAction.EDIT, { error("obsolete") }, { failures.add(it.exceptionOrNull()?.message) })
        f.worker.next(); f.loader.discard(); f.main.next()
        f.loader.load(MessageLoadAction.EDIT, { error("current") }, { failures.add(it.exceptionOrNull()?.message) })
        f.worker.next(); f.main.next()
        assertEquals(listOf("current"), failures)
    }

    @Test fun cancelingAnInFlightReadReleasesTheTaskWorkerBeforeItsServiceReplies() {
        val worker = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val completions = AtomicInteger()
        val response = CompletableFuture<JSONObject?>()
        val effects = ConversationEffects(object : ServiceGateway {
            override fun call(method: String, params: JSONObject, sessionId: String, callback: (Result<Any?>) -> Unit) =
                fail("This test only waits for the pending response")
        }, {}, { false })
        val loader = ConversationMessageLoader(worker) { it() }
        try {
            loader.load(MessageLoadAction.RETRY, { canceled ->
                started.countDown()
                effects.await(response, canceled, 120)
            }, { completions.incrementAndGet() })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            loader.discard()
            // A subsequent task can use this same worker without waiting for the attachment service timeout.
            worker.submit {}.get(2, TimeUnit.SECONDS)
            assertEquals(0, completions.get())
            assertFalse(response.isDone)
        } finally { response.complete(null); worker.shutdownNow() }
    }
}
