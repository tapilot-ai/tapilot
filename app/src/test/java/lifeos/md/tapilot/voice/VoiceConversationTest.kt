package lifeos.md.tapilot.voice

import lifeos.md.tapilot.speech.SpeechRecognition
import org.junit.Assert.*
import org.junit.Test

class VoiceConversationTest {
    private class Mic : SpeechRecognition {
        override val canRetry = false
        var starts = 0
        var cancels = 0
        var readyImmediately = true
        var ready: ((Result<Unit>) -> Unit)? = null
        var level: (Float) -> Unit = {}
        var transcript: (String) -> Unit = {}
        var boundary: (Boolean) -> Unit = {}
        var finished: ((Result<String>) -> Unit)? = null
        override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
            onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, onSpeechBoundary: (Boolean) -> Unit, callback: (Result<Unit>) -> Unit) {
            boundary = onSpeechBoundary
            starts++; level = onLevel; transcript = onTranscript; ready = callback
            if (readyImmediately) callback(Result.success(Unit))
        }
        override fun finish(callback: (Result<String>) -> Unit) { finished = callback }
        override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = Unit
        override fun cancel() { cancels++ }
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
        fun quiet() { repeat(17) { time += 100; mic.level(.02f); session.tick() } }
        fun ask(text: String) {
            mic.transcript(text); repeat(4) { mic.level(.7f); time += 100 }; quiet()
            session.tick(); mic.finished!!(Result.success(text)); player.completed!!(Result.success(Unit)); player.spoken.clear()
        }
    }
    @Test fun unchangedTranscriptSendsAfterThreeSecondsDespiteNoiseAndRepeatedResults() {
        val s = Setup(); s.start(); s.mic.transcript("这是回答")
        repeat(29) { s.time += 100; s.mic.level(.6f); s.mic.transcript("这是回答"); s.session.tick() }
        assertNull(s.mic.finished)
        s.time += 100; s.session.tick(); assertNotNull(s.mic.finished)
        s.mic.finished!!(Result.success("这是最终回答"))
        assertEquals(listOf("这是最终回答"), s.questions)
    }
    @Test fun newWordsResetThreeSecondFallbackAndHeldInputDoesNotSend() {
        val s = Setup(); s.start(); s.mic.transcript("第一句")
        s.time += 2900; s.mic.transcript("第一句还有后半句"); s.session.tick()
        s.time += 2900; s.session.tick(); assertNull(s.mic.finished)
        s.time += 200; s.session.tick(autoSend = false); assertNull(s.mic.finished)
        s.session.tick(); assertNotNull(s.mic.finished)
    }
    @Test fun releasedHoldShowsFinalTextForOneSecondBeforeSending() {
        val s = Setup(); s.start()
        s.session.finishUtterance(previewBeforeSend = true)
        s.time += 2500 // Recognition latency does not consume the preview interval.
        s.mic.finished!!(Result.success("最终文字"))
        assertEquals("最终文字", s.session.state.transcript)
        assertTrue(s.session.state.previewingTranscript)
        assertTrue(s.questions.isEmpty())
        s.time += 999; s.session.tick(); assertTrue(s.questions.isEmpty())
        s.time++; s.session.tick()
        assertEquals(listOf("最终文字"), s.questions)
        assertFalse(s.session.state.previewingTranscript)
        s.session.tick(); assertEquals(1, s.questions.size)
    }
    @Test fun endingDuringReleasedHoldPreviewCancelsScheduledSend() {
        val s = Setup(); s.start(); s.session.finishUtterance(previewBeforeSend = true)
        s.mic.finished!!(Result.success("不要发送"))
        s.session.stop(); s.time += 2000; s.session.tick()
        assertTrue(s.questions.isEmpty())
        assertFalse(s.session.state.previewingTranscript)
    }
    @Test fun backgroundingDuringPreviewPreservesTextWithoutSending() {
        val s = Setup(); s.start(); s.session.finishUtterance(previewBeforeSend = true)
        s.mic.finished!!(Result.success("保留文字"))
        s.session.setForeground(false); s.time += 2000; s.session.tick()
        s.session.setForeground(true); s.session.tick()
        assertTrue(s.questions.isEmpty())
        assertEquals("保留文字", s.session.state.transcript)
        assertFalse(s.session.state.previewingTranscript)
    }
    @Test fun providerPauseSendsRecognizedAnswerDespiteFluctuatingBackgroundNoise() {
        val s = Setup(); s.start(); s.mic.transcript("把它命名为工具分组")
        repeat(20) { s.time += 100; s.mic.level(if (it % 2 == 0) .34f else .56f); s.session.tick() }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.mic.boundary(true); s.session.tick()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("把它命名为工具分组"))
        assertEquals(listOf("把它命名为工具分组"), s.questions)
    }
    @Test fun providerPauseDoesNotSendHeldRecordingOrEmptyText() {
        val s = Setup(); s.start(); s.mic.boundary(true); s.session.tick()
        assertNull(s.mic.finished)
        s.mic.transcript("第一句"); s.session.tick(autoSend = false)
        assertNull(s.mic.finished)
        s.mic.boundary(false); s.session.tick()
        assertNull(s.mic.finished)
        s.mic.boundary(true); s.session.tick()
        assertNotNull(s.mic.finished)
    }
    @Test fun staleProviderPauseCannotSendAfterManualPauseOrNewRecording() {
        val s = Setup(); s.start(); s.mic.transcript("旧回答")
        val oldBoundary = s.mic.boundary
        s.session.pause(); oldBoundary(true); s.session.tick()
        assertNull(s.mic.finished)
        s.session.interrupt(); s.mic.transcript("新回答")
        oldBoundary(true); s.session.tick()
        assertNull(s.mic.finished)
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
        s.quiet(); assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }
    @Test fun quietRecognizedSpeechEndsAfterPauseWithoutReachingVolumeThreshold() {
        val s = Setup(); s.start(); s.mic.level(.1f); s.mic.transcript("小声回答")
        s.quiet()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("小声回答")); assertEquals(listOf("小声回答"), s.questions)
    }
    @Test fun quietOpeningSpeechNeedsAnActualDropBeforeSending() {
        val s = Setup(); s.start(); s.mic.transcript("小声说话")
        repeat(20) { s.time += 100; s.mic.level(.2f); s.session.tick() }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.quiet(); assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }
    @Test fun preparationDeadlineCancelsAndIgnoresLateCallbacks() {
        val s = Setup(); s.mic.readyImmediately = false; s.start()
        val cancels = s.mic.cancels
        s.time += 30_000; s.session.tick()
        assertEquals(cancels + 1, s.mic.cancels)
        s.mic.ready!!(Result.success(Unit)); s.mic.transcript("迟到结果"); s.mic.level(.8f)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertEquals("", s.session.state.transcript)
        assertTrue(s.questions.isEmpty())
    }
    @Test fun missingAudioCallbacksAreNotSilence() {
        val s = Setup(); s.start(); s.mic.transcript("还在说")
        repeat(4) { s.mic.level(.7f); s.time += 100 }
        s.time += 2000; s.session.tick()
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        assertNull(s.mic.finished)
    }
    @Test fun resumingSpeechCancelsPendingSilence() {
        val s = Setup(); s.start(); s.mic.transcript("第一句")
        repeat(12) { s.time += 100; s.mic.level(.02f); s.session.tick() }
        repeat(25) { s.time += 100; s.mic.transcript("第一句后继续说 $it"); s.mic.level(.15f); s.session.tick() }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.quiet(); assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }
    @Test fun steadyBackgroundAboveOldThresholdCanEndAnUtterance() {
        val s = Setup(); s.start()
        repeat(5) { s.time += 100; s.mic.level(.28f) }
        s.mic.transcript("画好了")
        repeat(5) { s.time += 100; s.mic.level(.65f) }
        repeat(17) { s.time += 100; s.mic.level(.28f); s.session.tick() }
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }
    @Test fun continuousSpeechTimesOutWithoutSendingOrRestarting() {
        val s = Setup(); s.start(); s.mic.transcript("尚未说完的内容")
        repeat(300) { s.time += 100; s.mic.transcript("尚未说完 $it"); s.mic.level(.7f); s.session.tick() }
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertTrue(s.session.state.listeningTimedOut)
        assertEquals("尚未说完 299", s.session.state.transcript)
        assertNull(s.mic.finished); assertTrue(s.questions.isEmpty()); assertEquals(1, s.mic.starts)
        s.session.setForeground(false); s.session.setForeground(true)
        assertEquals(1, s.mic.starts)
        s.session.resume(); assertEquals(2, s.mic.starts)
        assertFalse(s.session.state.listeningTimedOut)
    }
    @Test fun holdModeAlsoHasListeningDeadline() {
        val s = Setup(); s.start(); s.time += 30_000; s.session.tick(autoSend = false)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertTrue(s.session.state.listeningTimedOut)
    }
    @Test fun emptyRecognitionNeverAutomaticallyReopensPaidStream() {
        for (result in listOf(Result.success(""), Result.failure(Exception("No speech recognized")))) {
            val s = Setup(); s.start(); s.session.finishUtterance(); s.mic.finished!!(result)
            assertEquals(VoicePhase.Paused, s.session.state.phase)
            assertEquals(1, s.mic.starts); assertTrue(s.questions.isEmpty())
            assertTrue(s.session.state.noSpeechRecognized)
        }
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
    @Test fun continueSubmitsTheRecordedAnswerWithoutWaitingForSilence() {
        val s = Setup(); s.start(); s.ask("帮我安排提醒")
        s.session.update(listOf(VoiceReply("question", "几点提醒？", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        s.mic.transcript("明天八点")
        s.session.continueConversation()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("明天八点"))
        assertEquals(listOf("帮我安排提醒", "明天八点"), s.questions)
        assertFalse(s.session.state.transcriptPending)
        s.session.continueConversation()
        assertEquals(2, s.questions.size)
    }

    @Test fun continueAfterListeningTimeoutSendsVisibleAnswerOnceAndRejectsLateAsr() {
        val s = Setup(); s.start(); s.mic.transcript("按用途分组")
        val late = s.mic.transcript
        s.time += 30_000; s.session.tick(autoSend = false)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertTrue(s.session.state.transcriptPending)
        s.session.continueConversation(); s.session.continueConversation()
        late("迟到的识别")
        assertEquals(listOf("按用途分组"), s.questions)
        assertEquals("按用途分组", s.session.state.transcript)
        assertEquals(1, s.mic.starts)
    }

    @Test fun continueAfterFollowUpSendsConversationTurnRatherThanReplayingPreviousUtterance() {
        val s = Setup(); s.start(); s.ask("整理桌面")
        s.session.update(listOf(VoiceReply("question", "要按用途分组吗？", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        s.session.pause(); s.session.continueConversation()
        assertEquals(listOf("整理桌面", "继续"), s.questions)
        assertEquals(2, s.mic.starts)
        assertEquals(VoicePhase.Thinking, s.session.state.phase)
    }

    @Test fun continueInEnglishUsesTheSameConversation() {
        val s = Setup(); s.session.start("deepgram", "test-key", "en-US", emptyList())
        s.session.continueConversation()
        s.mic.finished!!(Result.success(""))
        assertEquals(listOf("Continue"), s.questions)
        assertEquals(VoicePhase.Thinking, s.session.state.phase)
    }

    @Test fun continueCannotApprovePhoneConfirmationOrRestartBusyMicrophone() {
        val s = Setup(); s.start(); s.ask("操作")
        s.session.update(emptyList(), true, "确认操作")
        s.player.completed!!(Result.success(Unit))
        s.session.continueConversation()
        assertEquals(listOf("操作"), s.questions)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        s.session.update(emptyList(), true, null)
        s.session.continueConversation()
        assertEquals(1, s.mic.starts)
        assertEquals(VoicePhase.Thinking, s.session.state.phase)
    }

    @Test fun recordingAgainDiscardsUnsentTranscriptWithoutSendingIt() {
        val s = Setup(); s.start(); s.mic.transcript("口误")
        s.session.pause(); s.session.interrupt()
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        assertFalse(s.session.state.transcriptPending)
        assertEquals("", s.session.state.transcript)
        assertTrue(s.questions.isEmpty())
    }

    @Test fun lateMicrophoneReadyDoesNotOverwriteTranscriptionOrDispatchAgain() {
        val s = Setup(); s.mic.readyImmediately = false; s.start()
        s.mic.transcript("回答")
        s.session.continueConversation(); s.mic.ready!!(Result.success(Unit))
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("回答"))
        assertEquals(listOf("回答"), s.questions)
    }

    @Test fun continuingAnExternalBusyTurnWaitsForItsReplyWithoutOpeningTheMicrophone() {
        val s = Setup(); s.start(); s.session.pause()
        s.session.update(listOf(VoiceReply("external", "", false)), true, null)
        s.session.continueConversation()
        assertEquals(1, s.mic.starts)
        assertTrue(s.questions.isEmpty())
        s.session.update(listOf(VoiceReply("external", "已完成", true)), false, null)
        assertEquals(listOf("已完成"), s.player.spoken)
    }

    @Test fun continueFinalizesAudioEvenBeforeTheFirstPartialTranscriptArrives() {
        val s = Setup(); s.start()
        s.session.continueConversation()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        assertTrue(s.questions.isEmpty())
        s.mic.finished!!(Result.success("我喜欢蓝色"))
        assertEquals(listOf("我喜欢蓝色"), s.questions)
    }

    @Test fun completedTaskAutomaticallyListensForANewTask() {
        val s = Setup(); s.start(); s.ask("打开设置")
        s.session.update(listOf(VoiceReply("done", "已打开设置", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        assertEquals(2, s.mic.starts)
        s.ask("下一个任务，打开相机")
        assertEquals(listOf("打开设置", "下一个任务，打开相机"), s.questions)
    }

    @Test fun holdReleaseThenFollowUpAutomaticallyListensForTheAnswer() {
        val s = Setup(); s.start()
        s.mic.transcript("整理桌面")
        s.session.finishUtterance(); s.mic.finished!!(Result.success("整理桌面"))
        s.player.completed!!(Result.success(Unit))
        s.session.update(listOf(VoiceReply("question", "按什么方式？", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.ask("按用途")
        assertEquals(listOf("整理桌面", "按用途"), s.questions)
    }

    @Test fun automaticallyReopenedListeningStillHasAnIdleDeadline() {
        val s = Setup(); s.start(); s.ask("任务")
        s.session.update(listOf(VoiceReply("done", "完成", true)), false, null)
        s.player.completed!!(Result.success(Unit))
        s.time += 30_000; s.session.tick()
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertTrue(s.session.state.listeningTimedOut)
        assertEquals(2, s.mic.starts)
        assertEquals(listOf("任务"), s.questions)
    }

    @Test fun foregroundReturnPreservesUnsentAnswerUntilExplicitContinue() {
        val s = Setup(); s.start(); s.mic.transcript("明天八点")
        s.session.setForeground(false); s.session.setForeground(true)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertEquals("明天八点", s.session.state.transcript)
        assertEquals(1, s.mic.starts)
        s.session.continueConversation()
        assertEquals(listOf("明天八点"), s.questions)
    }

    @Test fun continueWhileTaskBusyFinalizesNewAnswerInsteadOfDiscardingIt() {
        val s = Setup(); s.start(); s.ask("整理桌面")
        s.session.update(listOf(VoiceReply("task", "", false)), true, null)
        s.session.interrupt(); s.mic.transcript("只整理第一页")
        s.session.continueConversation()
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("只整理第一页"))
        assertEquals(listOf("整理桌面", "只整理第一页"), s.questions)
    }

    @Test fun busyPublicationDoesNotCancelAnAnswerBeingRecorded() {
        val s = Setup(); s.start(); s.mic.transcript("补充要求")
        s.session.update(listOf(VoiceReply("task", "", false)), true, null)
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.session.finishUtterance(); s.mic.finished!!(Result.success("补充要求"))
        assertEquals(listOf("补充要求"), s.questions)
    }

    @Test fun busyTaskResultDoesNotOverwritePausedUnsentAnswer() {
        val s = Setup(); s.start(); s.ask("原任务")
        s.session.interrupt(); s.mic.transcript("新要求")
        s.session.setForeground(false)
        s.session.update(listOf(VoiceReply("done", "旧任务完成", true)), false, null)
        s.session.setForeground(true)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertEquals("新要求", s.session.state.transcript)
        s.session.continueConversation()
        assertEquals(listOf("原任务", "新要求"), s.questions)
    }

    @Test fun amendmentDoesNotMarkRunningReplyAsAlreadyRead() {
        val s = Setup(); s.start(); s.ask("原任务")
        s.session.update(listOf(VoiceReply("task", "", false)), true, null)
        s.session.interrupt(); s.session.finishUtterance()
        s.mic.finished!!(Result.success("补充要求"))
        s.player.completed!!(Result.success(Unit))
        s.session.update(listOf(VoiceReply("task", "按补充要求完成", true)), false, null)
        assertEquals(VoicePhase.Speaking, s.session.state.phase)
        assertEquals("按补充要求完成", s.player.spoken.last())
    }

    @Test fun earlierTaskFinishingDoesNotCancelFinalAnswerRecognition() {
        val s = Setup(); s.start(); s.ask("原任务")
        s.session.interrupt(); s.session.finishUtterance()
        val finish = s.mic.finished!!
        s.session.update(listOf(VoiceReply("done", "旧任务完成", true)), false, null)
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        finish(Result.success("新要求"))
        assertEquals(listOf("原任务", "新要求"), s.questions)
    }

    @Test fun confirmationPlaybackDoesNotEraseUnsubmittedAnswer() {
        val s = Setup(); s.start(); s.mic.transcript("补充限制")
        s.session.update(emptyList(), true, "需要确认")
        s.player.completed!!(Result.success(Unit))
        s.session.update(emptyList(), false, null)
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertEquals("补充限制", s.session.state.transcript)
        s.session.continueConversation()
        assertEquals(listOf("补充限制"), s.questions)
    }

    @Test fun pauseSendsWithBackgroundAboveTheOldFixedNoiseFloor() {
        for (background in listOf(.35f, .45f, .6f)) {
            val s = Setup(); s.start()
            repeat(5) { s.time += 100; s.mic.level(background); s.session.tick() }
            s.mic.transcript("打开设置")
            repeat(6) { s.time += 100; s.mic.level(background + .2f); s.session.tick() }
            repeat(17) { s.time += 100; s.mic.level(background); s.session.tick() }
            assertEquals("background=$background", VoicePhase.Recognizing, s.session.state.phase)
            s.mic.finished!!(Result.success("打开设置"))
            assertEquals(listOf("打开设置"), s.questions)
        }
    }

    @Test fun latePartialTranscriptDoesNotRestartObservedSilence() {
        val s = Setup(); s.start(); s.mic.level(.02f)
        repeat(6) { s.time += 100; s.mic.level(.6f); s.session.tick() }
        repeat(17) {
            s.time += 100; s.mic.level(.02f)
            if (it == 12) s.mic.transcript("网络迟到的识别结果")
            s.session.tick()
        }
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
    }

    @Test fun backgroundAloneAndHeldSpeechDoNotAutoSend() {
        val s = Setup(); s.start()
        repeat(25) { s.time += 100; s.mic.level(.45f); s.session.tick() }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        assertNull(s.mic.finished)
        s.mic.transcript("还按着按钮")
        repeat(6) { s.time += 100; s.mic.level(.7f); s.session.tick(autoSend = false) }
        repeat(17) { s.time += 100; s.mic.level(.45f); s.session.tick(autoSend = false) }
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        s.session.finishUtterance()
        s.mic.finished!!(Result.success("还按着按钮"))
        assertEquals(listOf("还按着按钮"), s.questions)
    }

    @Test fun microphoneWarmupZerosDoNotPoisonTheAmbientNoiseBaseline() {
        val s = Setup(); s.start()
        repeat(3) { s.time += 100; s.mic.level(0f); s.session.tick() }
        repeat(5) { s.time += 100; s.mic.level(.45f); s.session.tick() }
        s.mic.transcript("测试成功")
        repeat(6) { s.time += 100; s.mic.level(.7f); s.session.tick() }
        repeat(17) { s.time += 100; s.mic.level(.45f); s.session.tick() }
        assertEquals(VoicePhase.Recognizing, s.session.state.phase)
        s.mic.finished!!(Result.success("测试成功"))
        assertEquals(listOf("测试成功"), s.questions)
    }

    @Test fun mutedRepliesKeepContinuousListeningAndUnmuteDoesNotReplayHistory() {
        val s = Setup(); s.session.setPlaybackEnabled(false); s.start()
        s.session.finishUtterance(); s.mic.finished!!(Result.success("任务"))
        assertEquals(listOf("任务"), s.questions)
        assertTrue(s.player.spoken.isEmpty())
        s.session.update(listOf(VoiceReply("done", "已完成", true)), false, null)
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        assertTrue(s.player.spoken.isEmpty())
        s.session.setPlaybackEnabled(true)
        assertTrue(s.player.spoken.isEmpty())
        s.ask("下一个任务")
        s.session.update(listOf(VoiceReply("done", "已完成", true), VoiceReply("next", "新回复", true)), false, null)
        assertEquals(listOf("新回复"), s.player.spoken)
    }

    @Test fun mutingCurrentReplyStopsPlaybackAndIgnoresLateCompletion() {
        val s = Setup(); s.start(); s.ask("任务")
        s.session.update(listOf(VoiceReply("done", "已完成", true)), false, null)
        val late = s.player.completed!!
        s.session.setPlaybackEnabled(false)
        assertEquals(VoicePhase.Listening, s.session.state.phase)
        val starts = s.mic.starts
        late(Result.success(Unit))
        assertEquals(starts, s.mic.starts)
        assertEquals(listOf("任务"), s.questions)
    }

    @Test fun muteDuringListeningPreservesTheMicrophoneAndUnsentAnswer() {
        val s = Setup(); s.start(); s.mic.transcript("我的回答")
        val cancels = s.mic.cancels
        s.session.setPlaybackEnabled(false); s.session.setPlaybackEnabled(true)
        assertEquals(cancels, s.mic.cancels)
        assertEquals(1, s.mic.starts)
        assertEquals("我的回答", s.session.state.transcript)
        assertTrue(s.session.state.transcriptPending)
    }

    @Test fun mutedConfirmationStillRequiresExplicitApproval() {
        val s = Setup(); s.session.setPlaybackEnabled(false); s.start()
        s.session.finishUtterance(); s.mic.finished!!(Result.success("任务"))
        s.session.update(emptyList(), true, "确认执行")
        assertEquals(VoicePhase.Paused, s.session.state.phase)
        assertTrue(s.player.spoken.isEmpty())
        s.session.continueConversation()
        assertEquals(listOf("任务"), s.questions)
    }

}
