package com.phoneagent.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Captures one 16 kHz mono PCM stream and optionally tees it to SpeechRecognizer. */
class VoiceAudioCapture(private val context: Context) {
    data class Session(val audioSource: ParcelFileDescriptor?, val file: File)

    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var readSide: ParcelFileDescriptor? = null
    private var writeSide: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var outputFile: File? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @SuppressLint("MissingPermission")
    fun start(
        teeToRecognizer: Boolean = true,
        onSpeechEnd: (() -> Unit)? = null,
        onSpeechStart: (() -> Unit)? = null,
        onPcm: ((ByteArray, Int) -> Unit)? = null,
        enablePlaybackEchoCancellation: Boolean = false,
        endSilenceMillis: () -> Long = { END_SILENCE_MILLIS },
        initialSilenceTimeoutMillis: Long = INITIAL_SILENCE_TIMEOUT_MILLIS,
    ): Session {
        stop(keepRecording = false)
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "设备不支持 16 kHz 语音录制" }
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(
                if (enablePlaybackEchoCancellation) MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minimum * 2, AUDIO_CHUNK_BYTES * 4))
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "麦克风录音器初始化失败" }
        if (enablePlaybackEchoCancellation) {
            echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
                runCatching { AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true } }.getOrNull()
            } else null
            noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
                runCatching { NoiseSuppressor.create(audioRecord.audioSessionId)?.apply { enabled = true } }.getOrNull()
            } else null
        }
        val pipe = if (teeToRecognizer) ParcelFileDescriptor.createPipe() else null
        val directory = File(context.filesDir, "voice-input").apply { mkdirs() }
        val file = File(directory, "voice-${UUID.randomUUID()}.wav")
        recorder = audioRecord
        readSide = pipe?.get(0)
        writeSide = pipe?.get(1)
        outputFile = file
        running.set(true)
        audioRecord.startRecording()
        worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runCatching {
                FileOutputStream(file).use { wav ->
                    wav.write(ByteArray(WAV_HEADER_SIZE))
                    val recognizerInput = pipe?.get(1)?.let { FileOutputStream(it.fileDescriptor) }
                    recognizerInput.use { speechPipe ->
                        // 40 ms at 16 kHz mono PCM: low-latency chunks matching
                        // common realtime voice transports without tiny JNI reads.
                        val buffer = ByteArray(AUDIO_CHUNK_BYTES)
                        val startedAt = SystemClock.elapsedRealtime()
                        var speechStarted = false
                        var lastSpeechAt = startedAt
                        val prefixAudio = ArrayDeque<ByteArray>()
                        while (running.get()) {
                            val count = audioRecord.read(buffer, 0, buffer.size)
                            if (count > 0) {
                                speechPipe?.write(buffer, 0, count)
                                onPcm?.invoke(buffer.copyOf(count), count)
                                if (!teeToRecognizer) {
                                    val now = SystemClock.elapsedRealtime()
                                    val rms = pcmRms(buffer, count)
                                    val chunk = buffer.copyOf(count)
                                    if (!speechStarted && rms >= SPEECH_RMS_THRESHOLD) {
                                        onSpeechStart?.invoke()
                                        speechStarted = true
                                        prefixAudio.forEach { wav.write(it) }
                                        prefixAudio.clear()
                                        wav.write(chunk)
                                        lastSpeechAt = now
                                    } else if (speechStarted) {
                                        wav.write(chunk)
                                        if (rms >= SPEECH_RMS_THRESHOLD) lastSpeechAt = now
                                    } else {
                                        prefixAudio.addLast(chunk)
                                        while (prefixAudio.size > PREFIX_CHUNKS) prefixAudio.removeFirst()
                                    }
                                    // The final WAV contains speech rather than an arbitrarily long
                                    // idle/agent-playback prefix; the streaming recognizer still sees all PCM.
                                    val speechFinished = speechStarted && now - lastSpeechAt >= endSilenceMillis().coerceIn(350L, 2_500L)
                                    val initialTimeout = !speechStarted && now - startedAt >= initialSilenceTimeoutMillis
                                    val maximumReached = speechStarted && now - startedAt >= MAX_RECORDING_MILLIS
                                    if (speechFinished || initialTimeout || maximumReached) {
                                        running.set(false)
                                        onSpeechEnd?.invoke()
                                    }
                                } else wav.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }, "PhoneAgent-VoiceCapture").also { it.start() }
        return Session(pipe?.get(0), file)
    }

    fun stop(keepRecording: Boolean): File? {
        running.set(false)
        runCatching { recorder?.stop() }
        runCatching { writeSide?.close() }
        runCatching { worker?.join(700) }
        runCatching { readSide?.close() }
        runCatching { recorder?.release() }
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        recorder = null
        echoCanceler = null
        noiseSuppressor = null
        readSide = null
        writeSide = null
        worker = null
        val file = outputFile.also { outputFile = null } ?: return null
        if (!keepRecording || file.length() <= WAV_HEADER_SIZE) {
            file.delete()
            return null
        }
        writeWavHeader(file, file.length() - WAV_HEADER_SIZE)
        return file
    }

    private fun writeWavHeader(file: File, pcmBytes: Long) {
        RandomAccessFile(file, "rw").use { output ->
            val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
            output.seek(0)
            output.writeBytes("RIFF")
            output.writeIntLE((pcmBytes + 36).toInt())
            output.writeBytes("WAVEfmt ")
            output.writeIntLE(16)
            output.writeShortLE(1)
            output.writeShortLE(CHANNELS)
            output.writeIntLE(SAMPLE_RATE)
            output.writeIntLE(byteRate)
            output.writeShortLE(CHANNELS * BITS_PER_SAMPLE / 8)
            output.writeShortLE(BITS_PER_SAMPLE)
            output.writeBytes("data")
            output.writeIntLE(pcmBytes.toInt())
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) = write(
        byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()),
    )

    private fun RandomAccessFile.writeShortLE(value: Int) = write(byteArrayOf(value.toByte(), (value shr 8).toByte()))

    private fun pcmRms(buffer: ByteArray, count: Int): Double {
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < count) {
            val value = ((buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)).toShort().toInt()
            sum += value.toDouble() * value
            samples++
            index += 4 // Sampling every other frame is sufficient for endpoint detection.
        }
        return if (samples == 0) 0.0 else kotlin.math.sqrt(sum / samples)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val WAV_HEADER_SIZE = 44
        const val AUDIO_CHUNK_BYTES = 1_280
        const val PREFIX_CHUNKS = 5
        const val SPEECH_RMS_THRESHOLD = 650.0
        const val END_SILENCE_MILLIS = 1_100L
        const val INITIAL_SILENCE_TIMEOUT_MILLIS = 6_000L
        const val MAX_RECORDING_MILLIS = 20_000L
    }
}
