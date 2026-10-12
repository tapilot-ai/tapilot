package lifeos.md.tapilot.voice

import org.junit.Assert.*
import org.junit.Test

class ReplySpeechTest {
    private class Player : SpeechPlayback {
        val texts = mutableListOf<String>()
        var done: (Result<Unit>) -> Unit = {}
        override fun speak(text: String, onStarted: () -> Unit, callback: (Result<Unit>) -> Unit) { texts.add(text); done = callback; onStarted() }
        override fun stop() = Unit
        override fun close() = Unit
    }
    @Test fun readsOnlyNewCompletedAnswerWithoutReplayingHistory() {
        val p = Player(); val s = ReplySpeech(p, { it() }, { _, _ -> })
        val old = VoiceReply("old", "历史", true)
        s.update("a", listOf(old), true)
        s.update("a", listOf(old, VoiceReply("new", "", false)), true)
        assertTrue(p.texts.isEmpty())
        val fresh = VoiceReply("new", "新回答", true)
        s.update("a", listOf(old, fresh), true); s.update("a", listOf(old, fresh), true)
        assertEquals(listOf("新回答"), p.texts)
    }
    @Test fun switchConversationDoesNotSpeakOldAnswers() {
        val p = Player(); val s = ReplySpeech(p, { it() }, { _, _ -> })
        s.update("a", emptyList(), true); s.update("a", listOf(VoiceReply("1", "回答", true)), true)
        s.update("b", listOf(VoiceReply("2", "旧回答", true)), true)
        assertEquals("", s.speakingId); assertEquals(listOf("回答"), p.texts)
    }
    @Test fun disabledOrVoiceModeDoesNotAutoplayLaterWhenReenabled() {
        val p = Player(); val s = ReplySpeech(p, { it() }, { _, _ -> })
        s.update("a", emptyList(), false)
        val replies = listOf(VoiceReply("1", "回答", true))
        s.update("a", replies, false); s.update("a", replies, true)
        assertTrue(p.texts.isEmpty())
        s.play(replies.first()); assertEquals(listOf("回答"), p.texts)
    }
    @Test fun lateCompletionCannotClearNewPlayback() {
        val p = Player(); val s = ReplySpeech(p, { it() }, { _, _ -> })
        s.play(VoiceReply("1", "一", true)); val late = p.done
        s.play(VoiceReply("2", "二", true)); late(Result.success(Unit))
        assertEquals("2", s.speakingId)
        s.stop(); p.done(Result.failure(Exception("late"))); assertEquals("", s.speakingId)
    }
}
