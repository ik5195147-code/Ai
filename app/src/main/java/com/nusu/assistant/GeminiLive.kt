package com.nusu.assistant

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Direct WebSocket client for the Gemini Live API. The key never leaves the phone except to Google. */
class GeminiLive(
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val systemPrompt: String,
    private val tools: JSONArray,
    private val listener: Listener
) {
    interface Listener {
        fun onReady()
        fun onAudio(pcm: ByteArray)
        fun onInterrupted()
        fun onToolCall(name: String, args: JSONObject): String
        fun onClosed(reason: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val toolExec = Executors.newSingleThreadExecutor()
    private var ws: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var closedNotified = false

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = sendSetup()
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            // Gemini Live sends its JSON as binary frames, so this one is required
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                notifyClosed("failure: ${t.message} ${response?.code ?: ""}")
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                notifyClosed("closed $code $reason")
        })
    }

    private fun sendSetup() {
        val setup = JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools))))
        ws?.send(setup.toString())
    }

    private fun handle(s: String) {
        val o = JSONObject(s)

        if (o.has("setupComplete")) { ready = true; listener.onReady(); return }

        o.optJSONObject("serverContent")?.let { sc ->
            if (sc.optBoolean("interrupted")) listener.onInterrupted()
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (i in 0 until parts.length()) {
                val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                listener.onAudio(Base64.decode(inline.getString("data"), Base64.DEFAULT))
            }
        }

        o.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            // run off the socket thread: phone actions can take seconds
            toolExec.execute {
                val responses = JSONArray()
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val result = try {
                        listener.onToolCall(c.getString("name"), c.optJSONObject("args") ?: JSONObject())
                    } catch (e: Exception) { "error: ${e.message}" }
                    responses.put(JSONObject()
                        .put("id", c.optString("id"))
                        .put("name", c.getString("name"))
                        .put("response", JSONObject().put("result", result)))
                }
                ws?.send(JSONObject().put("toolResponse",
                    JSONObject().put("functionResponses", responses)).toString())
            }
        }
    }

    fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val msg = JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("data", Base64.encodeToString(pcm16k, Base64.NO_WRAP))
            .put("mimeType", "audio/pcm;rate=16000")))
        ws?.send(msg.toString())
    }

    fun close() {
        ready = false
        ws?.close(1000, "bye")
        toolExec.shutdown()
    }

    private fun notifyClosed(reason: String) {
        if (closedNotified) return
        closedNotified = true
        ready = false
        listener.onClosed(reason)
    }
}
