package lifeos.md.tapilot.voice

import lifeos.md.tapilot.bridge.SpeechRecognition
import org.junit.Assert.*
import org.junit.Test

class VoiceConversationTest {
    private class Mic : SpeechRecognition {
        override val canRetry = false
        var starts = 0
        var level: (Float) -> Unit = {}
        var transcript: (String) -> Unit = {}
        var finished: ((Result<String>) -> Unit)? = null
        override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
            onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) {
            starts++; level = onLevel; transcript = onTranscript; callback(Result.success(Unit))
        }
        override fun finish(callback: (Result<String>) -> Unit) { finished = callback }
        override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
    private class Player : SpeechPlayback {
        val spoken = mutableListOf<String>()
        var completed: ((Result<Unit>) -> Unit)? = null
        override fun speak(text: String, onStarted: () -> Unit, callback: (Result<Unit>) -> Unit) {
            spoken.add(text); completed = callback; onStarted()
        }
        override fun stop() = Unit
        override fun close() = Unit
    }
    private class Setup {
        val mic = Mic(); val player = Player(); var time = 1L; val questions = mutableListOf<String>()
        val session = VoiceConversation(mic, player, { time }, { it() }, { questions.add(it) }, {})
        fun start(history: List<VoiceReply> = emptyList()) = session.start("volcengine", "test-key", "zh-CN", history)
        fun ask(text: String) {
            mic.transcript(text); repeat(4) { mic.level(.7f); time += 100 }; time += 1200
            session.tick(); mic.finished!!(Result.success(text)); player.completed!!(Result.success(Unit)); player.spoken.clear()
        }
    }
    @Test fun acknowledgementDoesNotDelayDispatchAndQueuesFastReply() {
        val s = Setup(); s.start(); s.session.finishUtterance(); s.mic.finished!!(Result.success("新指令"))
        assertEquals(listOf("新指令"), s.questions)
        assertEquals(listOf("收到。"), s.player.spoken)
        s.session.update(listOf(VoiceReply("id", "已完成", true)), false, null)
        assertEquals(listOf("收到。"), s.player.spoken)
        s.player.completed!!(Result.success(Unit))
        assertEquals(listOf("收到。", "已完成"), s.player.spoken)
    }
    @Test fun failedAcknowledgementStillReadsTheTaskResult() {
        val s = Setup(); s.start(); s.session.finishUtterance(); s.mic.finished!!(Result.success("指令"))
        s.session.update(listOf(VoiceReply("id", "结果", true)), false, null)
        s.player.completed!!(Result.failure(Exception("ack failed")))
        assertEquals(listOf("收到。", "结果"), s.player.spoken)
    }
    @Test fun conversationReadsFollowUpAndListensForNextAnswer() {
        val s = Setup(); s.start(); s.ask("帮我设提醒")
        s.session.update(listOf(VoiceReply("one", "你希望几点提醒？", true)), false, null)
        assertEquals(VoicePhase.Speaking, s.session.state.phase)
        s.player.completed!!(Result.success(Unit))
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.ask("明天八点")
        assertEquals(listOf("帮我设提醒", "明天八点"), s.questions)
        s.session.update(listOf(VoiceReply("one", "你希望几点提醒？", true), VoiceReply("two", "好的", true)), false, null)
        assertEquals(listOf("好的"), s.player.spoken)
    }
    @Test fun softOngoingSpeechIsNotCutOffWhenPartialTranscriptStopsUpdating() {
        val s = Setup(); s.start(); s.mic.transcript("还没说完")
        repeat(20) { s.time += 100; s.mic.level(.35f); s.session.tick() }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.time += 1201; s.session.tick(); assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }
    @Test fun quietRecognizedSpeechEndsAfterPauseWithoutReachingVolumeThreshold() {
        val s = Setup(); s.start(); s.mic.level(.1f); s.mic.transcript("小声回答")
        s.time += 1201; s.session.tick()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("小声回答")); assertEquals(listOf("小声回答"), s.questions)
    }
    @Test fun oldRepliesAndRepeatedPublishesAreNotRead() {
        val s = Setup(); val old = VoiceReply("old", "旧回答", true); s.start(listOf(old)); s.ask("新问题")
        s.session.update(listOf(old), false, null); assertTrue(s.player.spoken.isEmpty())
        val fresh = VoiceReply("new", "新回答", true)
        s.session.update(listOf(old, fresh), true, null); assertTrue(s.player.spoken.isEmpty())
        s.session.update(listOf(old, fresh), false, null); s.player.completed!!(Result.success(Unit))
        repeat(3) { s.session.update(listOf(old, fresh), false, null) }
        assertEquals(listOf("新回答"), s.player.spoken)
    }
    @Test fun interruptionIgnoresLatePlaybackCompletion() {
        val s = Setup(); s.start(); s.ask("问题"); s.session.update(listOf(VoiceReply("id", "回答", true)), false, null)
        val late = s.player.completed!!; s.session.interrupt(); val starts = s.mic.starts
        late(Result.success(Unit)); assertEquals(starts, s.mic.starts); assertEquals(VoicePhase.Listening, s.session.state.phase)
    }
    @Test fun endingConversationIgnoresLateRecognition() {
        val s = Setup(); s.start(); s.session.finishUtterance(); val late = s.mic.finished!!
        s.session.stop(); late(Result.success("不该发送")); assertTrue(s.questions.isEmpty()); assertFalse(s.session.state.active)
    }
    @Test fun foregroundReturnReadsAnswerCompletedDuringPhoneTask() {
        val s = Setup(); s.start(); s.ask("打开计算器"); s.session.setForeground(false)
        s.session.update(listOf(VoiceReply("id", "已打开计算器", true)), false, null)
        assertTrue(s.player.spoken.isEmpty()); s.session.setForeground(true)
        assertEquals(listOf("已打开计算器"), s.player.spoken)
    }
    @Test fun aNewForegroundSessionDoesNotInheritPreviousBackgroundPause() {
        val s = Setup(); s.start(); s.session.setForeground(false); s.session.stop(); s.start()
        assertEquals(VoicePhase.Listening, s.session.state.phase)
    }
    @Test fun manuallyPausedConversationStaysPausedAcrossForegroundChanges() {
        val s = Setup(); s.start(); s.session.pause(); s.session.setForeground(false); s.session.setForeground(true)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
    }
    @Test fun idleListeningPausesWithoutSendingTask() {
        val s = Setup(); s.start(); s.time += 49_000; s.session.tick()
        assertEquals(1, s.mic.starts); assertEquals(VoicePhase.Paused, s.session.state.phase); assertTrue(s.questions.isEmpty())
    }
    @Test fun confirmationIsSpokenWithoutAutoApprovingAndResumesAfterConfirmation() {
        val s = Setup(); s.start(); s.ask("操作"); s.session.update(emptyList(), true, "确认操作")
        s.player.completed!!(Result.success(Unit)); assertEquals(VoicePhase.Paused, s.session.state.phase)
        s.session.update(listOf(VoiceReply("id", "执行完成", true)), false, null)
        assertEquals(2, s.player.spoken.size); assertEquals(1, s.questions.size)
    }
    @Test fun retriedTaskAlsoSpeaksItsNewResult() {
        val s = Setup(); s.start(); s.ask("问题")
        s.session.update(listOf(VoiceReply("failed", "请求超时", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        s.session.update(listOf(VoiceReply("retry", "", false)), true, null)
        s.session.update(listOf(VoiceReply("retry", "重试成功", true)), false, null)
        assertEquals(listOf("请求超时", "重试成功"), s.player.spoken)
    }
    @Test fun playbackFailureCanRetryWithoutSendingQuestionAgain() {
        val s = Setup(); s.start(); s.ask("问题"); s.session.update(listOf(VoiceReply("id", "回答", true)), false, null)
        s.player.completed!!(Result.failure(Exception("403"))); assertEquals(VoicePhase.Error, s.session.state.phase)
        s.session.resume(); assertEquals(listOf("回答", "回答"), s.player.spoken); assertEquals(1, s.questions.size)
    }
}
