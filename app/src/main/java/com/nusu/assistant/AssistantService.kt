package com.nusu.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Foreground service: keeps the mic + Gemini session alive while the screen is off or another app is open. */
class AssistantService : Service() {

    companion object {
        val status = MutableStateFlow("Stopped")
        const val ACTION_STOP = "com.nusu.assistant.STOP"
        private const val CHANNEL = "nusu"

        private const val SYSTEM_PROMPT = """
You are NUSU, a friendly voice assistant that controls the user's Android phone.
Speak in the user's language (Bangla by default). Keep replies short.
Use the tools to act. For multi-step tasks: open_app, then read_screen, then tap_text / type_text / scroll,
then read_screen again to check it worked. Use English app names when calling open_app.
Before sending a message or placing a call, confirm with the user unless they were explicit.
If a tool returns an error, tell the user briefly what failed.
"""
    }

    private var live: GeminiLive? = null
    private var audio: AudioIO? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        ServiceCompat.startForeground(
            this, 1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        if (live == null) startSession()
        return START_STICKY
    }

    private fun startSession() {
        val store = SecureStore(this)
        if (store.apiKey.isBlank()) {
            status.value = "Add your Gemini API key first"
            stopSelf(); return
        }
        val tools = PhoneTools(this)
        status.value = "Connecting..."

        audio = AudioIO { chunk -> live?.sendAudio(chunk) }
        live = GeminiLive(
            store.apiKey, store.model, store.voice, SYSTEM_PROMPT, tools.declarations(),
            object : GeminiLive.Listener {
                override fun onReady() { audio?.start(); status.value = "Listening" }
                override fun onAudio(pcm: ByteArray) { audio?.play(pcm) }
                override fun onInterrupted() { audio?.flush() }
                override fun onToolCall(name: String, args: JSONObject): String {
                    status.value = "Running: $name"
                    val r = tools.execute(name, args)
                    status.value = "Listening"
                    return r
                }
                override fun onClosed(reason: String) {
                    status.value = reason
                    stopSelf()
                }
            }
        )
        live?.connect()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "NUSU", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NUSU is listening")
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        audio?.stop(); audio = null
        live?.close(); live = null
        super.onDestroy()
    }
}
