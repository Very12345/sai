package com.phoneagent.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zero-cost final transcription loaded from the independently uninstallable sai Voice Pack. */
class LocalAsrManager(private val context: Context) : AutoCloseable {
    sealed interface State {
        data class Downloading(val downloaded: Long, val total: Long) : State
        data object Verifying : State
        data object Extracting : State
        data object Ready : State
        data object Recognizing : State
    }

    @Volatile private var recognizer: OfflineRecognizer? = null

    fun isReady(): Boolean = runCatching {
        val assets = VoiceModelPack.context(context)?.assets ?: return@runCatching false
        assets.open(MODEL_ASSET).use { it.read() >= 0 } && assets.open(TOKENS_ASSET).use { it.read() >= 0 }
    }.getOrDefault(false)

    suspend fun ensureInstalled(onState: (State) -> Unit) {
        check(isReady()) { "请安装可选的 sai Voice Pack 后使用离线语音" }
        onState(State.Ready)
    }

    suspend fun transcribe(wavFile: File, onState: (State) -> Unit = {}): String = withContext(Dispatchers.Default) {
        check(isReady()) { "sai Voice Pack 未安装或模型不完整" }
        onState(State.Recognizing)
        val samples = readSaiWav(wavFile)
        require(samples.isNotEmpty()) { "录音内容为空" }
        val engine = getOrCreateRecognizer()
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    @Synchronized
    private fun getOrCreateRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(model = MODEL_ASSET),
                tokens = TOKENS_ASSET,
                numThreads = 2,
                provider = "cpu",
                modelType = "paraformer",
            ),
            decodingMethod = "greedy_search",
        )
        val assets = requireNotNull(VoiceModelPack.context(context)) { "sai Voice Pack 未安装" }.assets
        return OfflineRecognizer(assets, config).also { recognizer = it }
    }

    private fun readSaiWav(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > WAV_HEADER_BYTES && bytes.copyOfRange(0, 4).decodeToString() == "RIFF") {
            "不支持的录音格式"
        }
        return FloatArray((bytes.size - WAV_HEADER_BYTES) / 2) { index ->
            val offset = WAV_HEADER_BYTES + index * 2
            ((bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)).toShort() / 32768f
        }
    }

    override fun close() = synchronized(this) {
        recognizer?.release()
        recognizer = null
    }

    companion object {
        const val DOWNLOAD_DESCRIPTION = "Paraformer 中英双语终稿模型位于可独立卸载的 sai Voice Pack。"
        private const val MODEL_ASSET = "models/paraformer/model.int8.onnx"
        private const val TOKENS_ASSET = "models/paraformer/tokens.txt"
        private const val SAMPLE_RATE = 16_000
        private const val WAV_HEADER_BYTES = 44
    }
}
