package com.jarvis.voiceassistant.data

/**
 * Represents the state of model loading
 */
sealed class ModelLoadState {
    object NotLoaded : ModelLoadState()
    data class Downloading(val progress: Float) : ModelLoadState() // 0.0 to 1.0
    object Loading : ModelLoadState()
    data class Ready(val modelPath: String) : ModelLoadState()
    data class Error(val message: String) : ModelLoadState()
}

