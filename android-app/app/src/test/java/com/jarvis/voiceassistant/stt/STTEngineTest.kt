package com.jarvis.voiceassistant.stt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class STTEngineTest {

    private lateinit var sttEngine: ISTTEngine

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        sttEngine = STTEngine(app) { null }
    }

    @Test
    fun `initial state is not initialized`() {
        assertFalse(sttEngine.isInitialized())
    }

    @Test
    fun `initialize fails without model dir`() = runTest {
        val result = sttEngine.initialize()
        assertTrue(result.isFailure)
    }

    @Test
    fun `transcribe requires initialization`() = runTest {
        val audioData = ByteArray(STTEngine.MIN_AUDIO_BYTES)
        val result = sttEngine.transcribe(audioData)
        assertTrue(result.isFailure)
    }

    @Test
    fun `release can be called multiple times`() {
        sttEngine.release()
        sttEngine.release()
    }
}
