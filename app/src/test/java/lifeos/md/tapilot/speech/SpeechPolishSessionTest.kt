package lifeos.md.tapilot.speech

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class SpeechPolishSessionTest {
    private fun await(latch: CountDownLatch) { assertTrue("Timed out", latch.await(2, TimeUnit.SECONDS)) }
    @Test fun finalPolishWaitsForPreviewAndIncludesCompleteTail() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val requests = CopyOnWriteArrayList<String>(); var final = ""
        val session = SpeechPolishSession({ text -> requests.add(text); if (text == "开头") { started.countDown(); release.await(2, TimeUnit.SECONDS) }; "整理$text" }, {}, pauseMs = 1, intervalMs = 10)
        try {
            session.update("开头"); await(started)
            session.update("开头最后一个字")
            session.finish("开头最后一个字") { final = it; done.countDown() }
            assertEquals(listOf("开头"), requests.toList())
            release.countDown(); await(done)
            assertEquals(listOf("开头", "开头最后一个字"), requests.toList())
            assertEquals("整理开头最后一个字", final)
        } finally { release.countDown(); session.cancel() }
    }
    @Test fun previewsKeepLiveTailButDropPrefixWhenAsrCorrectsEarlierWords() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val rendered = CountDownLatch(1)
        val displays = CopyOnWriteArrayList<String>()
        val session = SpeechPolishSession({ started.countDown(); release.await(2, TimeUnit.SECONDS); "今天" }, { text -> displays.add(text); if (text == "今天下午") rendered.countDown() }, pauseMs = 1, intervalMs = 3000)
        try {
            session.update("嗯今天"); await(started); session.update("嗯今天下午"); release.countDown(); await(rendered)
            session.update("明天下午"); assertEquals("明天下午", displays.last())
        } finally { release.countDown(); session.cancel() }
    }
    @Test fun cancellationDuringFinalPolishCannotCommitOrDisplayLateResult() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val ended = CountDownLatch(1)
        var committed = false; val displays = CopyOnWriteArrayList<String>()
        val session = SpeechPolishSession({ started.countDown(); try { release.await(2, TimeUnit.SECONDS) } catch (_: InterruptedException) {} ; ended.countDown(); "late" }, displays::add)
        session.finish("original") { committed = true }
        await(started); session.cancel(); release.countDown(); await(ended)
        assertFalse(committed); assertTrue(displays.isEmpty())
    }
    @Test fun polishFailureCommitsRawOnce() {
        val done = CountDownLatch(1); var committed = ""; var count = 0
        val session = SpeechPolishSession({ throw java.io.IOException() }, {})
        try {
            session.finish("保留原文") { committed = it; count++; done.countDown() }
            session.finish("duplicate") { count++ }
            await(done); assertEquals("保留原文", committed); assertEquals(1, count)
        } finally { session.cancel() }
    }
}
