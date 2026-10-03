package lifeos.md.tapilot.bridge

import org.junit.Assert.*
import org.junit.Test

class SpeechServiceFailureTest {
    @Test fun resourcePermissionIsNotMisreportedAsAnInvalidKey() {
        val result = SpeechServiceFailure.message("volcengine", 403, "requested resource not granted", "request-123")
        assertTrue(result.contains("未获语音资源权限"))
        assertTrue(result.contains("request-123"))
        assertFalse(result.contains("鉴权失败"))
    }
    @Test fun rawResponseAndUntrustedRequestIdNeverReachTheInterface() {
        val result = SpeechServiceFailure.message("deepgram", 403, "secret=do-not-print", "credential:do-not-print")
        assertTrue(result.contains("项目权限"))
        assertFalse(result.contains("do-not-print"))
    }
}
