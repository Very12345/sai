package com.phoneagent.app

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/** Low-latency transcription loaded from the independently uninstallable sai Voice Pack. */
class StreamingAsrManager(private val context: Context) : AutoCloseable {
    @Volatile private var recognizer: OnlineRecognizer? = null
    @Volatile private var stream: OnlineStream? = null

    fun isReady(): Boolean = REQUIRED_ASSETS.all { asset ->
        runCatching { VoiceModelPack.context(context)?.assets?.open(asset)?.use { it.read() >= 0 } == true }.getOrDefault(false)
    }

    suspend fun ensureInstalled(onProgress: (Long, Long, String) -> Unit = { _, _, _ -> }) {
        check(isReady()) { "请安装可选的 sai Voice Pack 后使用离线语音" }
        onProgress(1, 1, "内置流式模型已就绪")
    }

    @Synchronized
    fun begin() {
        check(isReady()) { "sai Voice Pack 未安装或模型不完整" }
        stream?.release()
        stream = engine().createStream()
    }

    @Synchronized
    fun acceptPcm16(bytes: ByteArray, count: Int): String {
        val active = stream ?: return ""
        val samples = FloatArray(count / 2) { index ->
            val offset = index * 2
            ((bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)).toShort() / 32768f
        }
        active.acceptWaveform(samples, SAMPLE_RATE)
        val engine = engine()
        while (engine.isReady(active)) engine.decode(active)
        return engine.getResult(active).text.trim()
    }

    @Synchronized
    fun finish(): String {
        val active = stream ?: return ""
        active.inputFinished()
        val engine = engine()
        while (engine.isReady(active)) engine.decode(active)
        val result = engine.getResult(active).text.trim()
        active.release()
        stream = null
        return result
    }

    private fun engine(): OnlineRecognizer {
        recognizer?.let { return it }
        val model = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = ENCODER_ASSET,
                decoder = DECODER_ASSET,
                joiner = JOINER_ASSET,
            ),
            tokens = TOKENS_ASSET,
            numThreads = 2,
            provider = "cpu",
            modelType = "zipformer2",
        )
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
            modelConfig = model,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        val assets = requireNotNull(VoiceModelPack.context(context)) { "sai Voice Pack 未安装" }.assets
        return OnlineRecognizer(assets, config).also { recognizer = it }
    }

    override fun close() = synchronized(this) {
        stream?.release(); stream = null
        recognizer?.release(); recognizer = null
    }

    companion object {
        const val DOWNLOAD_DESCRIPTION = "中英双语 Zipformer 位于可独立卸载的 sai Voice Pack。"
        private const val ENCODER_ASSET = "models/zipformer/encoder.int8.onnx"
        private const val DECODER_ASSET = "models/zipformer/decoder.onnx"
        private const val JOINER_ASSET = "models/zipformer/joiner.int8.onnx"
        private const val TOKENS_ASSET = "models/zipformer/tokens.txt"
        private val REQUIRED_ASSETS = listOf(ENCODER_ASSET, DECODER_ASSET, JOINER_ASSET, TOKENS_ASSET)
        private const val SAMPLE_RATE = 16_000
    }
}
