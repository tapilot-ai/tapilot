package lifeos.md.tapilot

import android.app.Activity
import lifeos.md.tapilot.bridge.SpeechRecognition
import lifeos.md.tapilot.speech.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** Only in the isolated verification APK: real polish pipeline, deterministic API response. */
internal fun verificationPolishing(activity: Activity, recognition: SpeechRecognition): SpeechRecognition =
    PolishingSpeechRecognition(recognition) { display ->
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            val transcript = body.getJSONArray("messages").getJSONObject(1).getString("content")
            val scenario = activity.intent.getStringExtra("polishScenario") ?: "success"
            if (scenario == "slow") Thread.sleep(2500)
            val result = if (transcript.endsWith("两个字")) "整理后的语音，保留最后两个字。" else "整理后的语音"
            val response = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", result))))
            activity.openFileOutput("polish-request.json", 0).use { it.write(JSONObject().put("transcript", transcript)
                .put("result", if (scenario == "failure") transcript else result).put("scenario", scenario).toString().toByteArray()) }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (scenario == "failure") 503 else 200)
                .message("verification").body(response.toString().toResponseBody()).build()
        }.build()
        val polisher = SpeechPolisher(SpeechPolishSettings(enabled = true), "synthetic-key", client)
        SpeechPolishSession(polisher::polish, display, polisher::cancel)
    }
