package org.astian.suite.maps.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralRuntimeTest {
    private val allInOne = "arm64-v8a,armeabi-v7a"

    @Test fun `arm phones, 64 and 32 bit, carry the voice runtime`() {
        assertTrue(NeuralRuntime.supports(listOf("arm64-v8a", "armeabi-v7a", "armeabi"), allInOne))
        assertTrue(NeuralRuntime.supports(listOf("armeabi-v7a", "armeabi"), allInOne))
    }

    @Test fun `an x86 device on the all-in-one build is told, not offered a download`() {
        assertFalse(NeuralRuntime.supports(listOf("x86_64", "arm64-v8a"), allInOne))
        assertTrue(NeuralRuntime.supports(listOf("x86_64", "arm64-v8a"), "arm64-v8a,armeabi-v7a,x86,x86_64"))
        assertFalse(NeuralRuntime.supports(emptyList(), allInOne))
    }
}
