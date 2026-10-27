package com.nusu.assistant

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Mic: 16 kHz mono PCM16 -> Gemini.  Speaker: 24 kHz mono PCM16 <- Gemini. */
class AudioIO(private val onMic: (ByteArray) -> Unit) {
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        val inMin = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // VOICE_COMMUNICATION turns on echo cancellation, so the AI doesn't hear itself
        rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, 6400)
        )
        val outMin = AudioTrack.getMinBufferSize(
            24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(outMin, 9600))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        running = true
        track?.play()
        rec?.startRecording()

        Thread {
            val buf = ByteArray(3200) // 100 ms
            while (running) {
                val n = try { rec?.read(buf, 0, buf.size) ?: break } catch (e: Exception) { break }
                if (n > 0) onMic(buf.copyOf(n)) else if (n < 0) break
            }
        }.start()

        Thread {
            while (running) {
                val b = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                try { track?.write(b, 0, b.size) } catch (e: Exception) { break }
            }
        }.start()
    }

    fun play(pcm: ByteArray) { queue.offer(pcm) }

    /** User interrupted the AI: drop whatever is still queued. */
    fun flush() {
        queue.clear()
        try { track?.pause(); track?.flush(); track?.play() } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        rec = null; track = null
    }
}
