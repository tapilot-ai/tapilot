package lifeos.md.tapilot.bridge

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelTransportTest {
    @Test
    fun `a cancelled request never starts network work`() {
        val transport = ModelTransport()
        try {
            val completed = CountDownLatch(1)
            var error: Throwable? = null
            transport.cancelRequest("request-1")

            transport.execute("request-1", "session-1", JSONObject()) { result ->
                error = result.exceptionOrNull()
                completed.countDown()
            }

            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertTrue(error is ModelTransport.RequestCancelledException)
        } finally {
            transport.close()
        }
    }
}
