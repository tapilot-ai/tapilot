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
        val requests = CopyOnWriteArrayList<String>(); val displays = CopyOnWriteArrayList<String>(); var final = ""
        val session = SpeechPolishSession({ text -> requests.add(text); if (text == "开头") { started.countDown(); release.await(2, TimeUnit.SECONDS) }; "整理$text" }, displays::add, pauseMs = 1, intervalMs = 10)
        try {
            session.update("开头"); await(started)
            session.finish("开头最后一个字") { final = it; done.countDown() }
            assertEquals("开头最后一个字", displays.last())
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
        assertFalse(committed); assertEquals(listOf("original"), displays.toList())
    }
    @Test fun finalBubbleStaysVisibleAndCanStillBeCancelled() {
        val displayed = CountDownLatch(1); val committed = CountDownLatch(1)
        val displays = CopyOnWriteArrayList<String>()
        val session = SpeechPolishSession({ "整理后的完整文字" }, {
            displays.add(it)
            if (it == "整理后的完整文字") displayed.countDown()
        }, completionDisplayMs = 150)
        try {
            session.finish("完整文字") { committed.countDown() }
            await(displayed)
            assertEquals(1L, committed.count)
            session.cancel()
            assertFalse(committed.await(250, TimeUnit.MILLISECONDS))
            assertEquals(listOf("完整文字", "整理后的完整文字"), displays.toList())
        } finally { session.cancel() }
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
