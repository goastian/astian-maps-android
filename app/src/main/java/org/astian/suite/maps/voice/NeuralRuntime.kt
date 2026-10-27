package org.astian.suite.maps.voice

import org.astian.suite.maps.BuildConfig

/**
 * Whether this build carries the neural-voice runtime (sherpa-onnx + ONNX Runtime) for the chip
 * the app runs on. The app runs as the device's first supported ABI, since every other native
 * library ships for all four. Where it is false, no voice is offered for download: the model
 * would download and then never load, and directions use the phone's own voice (SPEC 9).
 */
object NeuralRuntime {
    val supported: Boolean by lazy { supports(android.os.Build.SUPPORTED_ABIS.toList(), BuildConfig.TTS_ABIS) }

    internal fun supports(deviceAbis: List<String>, packaged: String): Boolean {
        val primary = deviceAbis.firstOrNull() ?: return false
        return primary in packaged.split(',').map { it.trim() }
    }
}
