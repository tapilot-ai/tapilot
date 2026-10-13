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
    @Test fun obsoletePreviewCannotReplaceResumedText() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val requestedLatest = CountDownLatch(1)
        val displays = CopyOnWriteArrayList<String>(); val requests = CopyOnWriteArrayList<String>()
        val session = SpeechPolishSession({ text ->
            requests.add(text)
            if (text == "嗯今天") { started.countDown(); release.await(2, TimeUnit.SECONDS); "旧结果" }
            else { "新全文" }
        }, { text -> displays.add(text); if (text == "新全文") requestedLatest.countDown() }, pauseMs = 1, intervalMs = 10)
        try {
            session.update("嗯今天"); await(started); session.update("嗯今天下午")
            assertEquals("嗯今天下午", displays.last())
            release.countDown(); await(requestedLatest)
            assertFalse(displays.contains("旧结果下午"))
            assertEquals(listOf("嗯今天", "嗯今天下午"), requests.toList())
        } finally { release.countDown(); session.cancel() }
    }
    @Test fun returningToSameTextDoesNotReuseAnOlderRevision() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val requests = CopyOnWriteArrayList<String>(); val displays = CopyOnWriteArrayList<String>()
        val currentDisplayed = CountDownLatch(1)
        val session = SpeechPolishSession({ text ->
            requests.add(text)
            if (requests.size == 1) { started.countDown(); release.await(2, TimeUnit.SECONDS); "旧的周一" }
            else "最新周一"
        }, { text -> displays.add(text); if (text == "最新周一") currentDisplayed.countDown() }, pauseMs = 1, intervalMs = 10, completionDisplayMs = 1)
        try {
            session.update("星期一"); await(started)
            session.update("星期二"); session.update("星期一")
            release.countDown(); await(currentDisplayed)
            session.finish("星期一") { assertEquals("最新周一", it); done.countDown() }
            release.countDown(); await(done)
            assertFalse(displays.contains("旧的周一"))
            assertEquals(2, requests.size)
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
    @Test fun releaseReusesCurrentInFlightRevision() {
        for (fail in listOf(false, true)) {
            val started = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
            val requests = CopyOnWriteArrayList<String>(); var result = ""
            val session = SpeechPolishSession({ text ->
                requests.add(text); started.countDown(); release.await(2, TimeUnit.SECONDS)
                if (fail) throw java.io.IOException("unavailable")
                "整理后的全文"
            }, {}, pauseMs = 1, intervalMs = 10, completionDisplayMs = 1)
            try {
                session.update("完整全文"); await(started)
                session.finish("完整全文") { result = it; done.countDown() }
                release.countDown(); await(done)
                assertEquals(listOf("完整全文"), requests.toList())
                assertEquals(if (fail) "完整全文" else "整理后的全文", result)
            } finally { release.countDown(); session.cancel() }
        }
    }
}
