package com.jarvis.voiceassistant

import android.app.Application
import com.runanywhere.sdk.foundation.bridge.CppBridge
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.storage.AndroidPlatformContext
import java.io.File

/**
 * Application entry point. Initializes RunAnywhere SDK for on-device LLM (LlamaCPP/GGUF).
 *
 * Order (per RunAnywhere docs):
 * 1. AndroidPlatformContext.initialize(context)
 * 2. CppBridge.initialize() (Phase 1 - loads native lib, platform adapter)
 * 3. CppBridgeModelPaths.setBaseDirectory(filesDir/runanywhere)
 * 4. LlamaCPP.register()
 */
class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initRunAnywhere()
    }

    private fun initRunAnywhere() {
        try {
            AndroidPlatformContext.initialize(this)
            CppBridge.initialize(CppBridge.Environment.DEVELOPMENT)
            val runanywhereDir = File(filesDir, "runanywhere").also { it.mkdirs() }
            CppBridgeModelPaths.setBaseDirectory(runanywhereDir.absolutePath)
            LlamaCPP.register()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "RunAnywhere init failed", e)
        }
    }

    companion object {
        private const val TAG = "JarvisApp"
    }
}
