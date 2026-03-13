# Android Voice Assistant Plan - Issues & Fixes

## Critical Issues Found

### 🔴 CRITICAL: Threading & Concurrency
**Issue**: All blocking operations (audio, STT, LLM) will freeze UI
**Fix**: 
- Use Kotlin Coroutines with `viewModelScope`
- `Dispatchers.IO` for audio/STT/LLM
- `Dispatchers.Main` for UI updates
- Add to Step 4: "Use coroutines for all async operations"

### 🔴 CRITICAL: Model Format Mismatch
**Issue**: Python PyTorch models won't work on Android
**Fix**:
- **STT**: Convert Moonshine to ONNX or use whisper.cpp (has Android support)
- **LLM**: Convert SmolLM to GGUF format for llama.cpp, or use compatible model
- Add new Step 0: "Convert models to Android-compatible formats"

### 🔴 CRITICAL: JNI Integration Missing
**Issue**: Both STT and LLM need native code bindings
**Fix**:
- Add JNI wrapper layer planning
- Use existing Android libraries where possible:
  - whisper.cpp (C++ with Android support)
  - llama.cpp (C++ with Android support)
- Add to Step 10: "Build JNI bindings for native libraries"

### 🟡 HIGH: Permissions Not Mentioned
**Issue**: Microphone permission required but not in plan
**Fix**: Add to Step 2 or Step 5:
```kotlin
// Request RECORD_AUDIO permission
// Handle permission denied gracefully
```

### 🟡 HIGH: Model Loading Strategy
**Issue**: Large models need initialization and lifecycle management
**Fix**: Add to Step 8:
- Model loading state (loading/ready/error)
- Progress indicator
- Lifecycle-aware loading (load on app start, unload on low memory)

### 🟡 HIGH: Audio Format Conversion
**Issue**: AudioRecord outputs PCM int16, STT may need float32
**Fix**: Add audio preprocessing step:
```kotlin
fun convertPcmToFloat(pcm: ShortArray): FloatArray {
    return pcm.map { it / 32768.0f }.toFloatArray()
}
```

### 🟡 MEDIUM: Error Handling Missing
**Issue**: No error handling for failures
**Fix**: Add error states to ViewModel:
- `sealed class AssistantState { Loading, Success, Error }`
- User-friendly error messages

### 🟡 MEDIUM: Memory Management
**Issue**: 3B+ parameter models can cause OOM
**Fix**:
- Use quantized models (Q4/Q5 GGUF)
- Monitor memory usage
- Implement model unloading on low memory

### 🟡 MEDIUM: STT Engine Choice
**Issue**: Moonshine is PyTorch-only, no Android native version
**Fix**: 
- Option 1: Use whisper.cpp (recommended, has Android support)
- Option 2: Build custom Moonshine JNI wrapper (complex)
- Update Step 7 to reflect chosen approach

### 🟡 MEDIUM: LLM Model Compatibility
**Issue**: SmolLM may not work directly with llama.cpp
**Fix**:
- Convert SmolLM to GGUF using `llama.cpp/convert_hf_to_gguf.py`
- Or use a model already compatible with llama.cpp
- Test conversion before Step 10

## Recommended Plan Modifications

### Add Step 0: Model Preparation
1. Convert SmolLM to GGUF format
2. Choose STT solution (whisper.cpp recommended)
3. Quantize models (Q4 for LLM, appropriate for STT)
4. Test models on desktop first

### Modify Step 4: Add Coroutines
```kotlin
class AssistantController(
    private val viewModel: AssistantViewModel
) {
    suspend fun handleUserSpeech(text: String) {
        viewModel.addUserMessage(text)
        val response = withContext(Dispatchers.IO) {
            llm.generate(text)
        }
        viewModel.addAssistantMessage(response)
    }
}
```

### Modify Step 5: Add Permissions
- Request RECORD_AUDIO permission
- Handle permission denied state
- Show permission rationale if needed

### Modify Step 7: Choose STT Solution
- **Recommended**: whisper.cpp (easier integration)
- **Alternative**: Custom Moonshine JNI (more work)

### Modify Step 8: Add Model Loading
- Initialize LLM model on app start (background)
- Show loading state in UI
- Handle model loading errors

### Modify Step 10: Add JNI Integration
- Build native libraries (whisper.cpp, llama.cpp)
- Create JNI wrapper functions
- Handle native library loading errors

## Additional Recommendations

1. **Start with smaller models**: Test with 1B-2B models first
2. **Add logging**: Use Timber or similar for debugging
3. **Add analytics**: Track performance metrics
4. **Progressive enhancement**: Get basic flow working, then optimize
5. **Test on real devices**: Emulators may not reflect real performance

## Dependencies to Add

```gradle
// Coroutines
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

// Audio
// (AudioRecord is built-in)

// Permissions
implementation "com.google.accompanist:accompanist-permissions:0.32.0"

// Lifecycle
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"
```

## Estimated Time Adjustments

- Step 0 (Model Prep): +2-4 hours
- Step 4 (Add Coroutines): +30 minutes
- Step 5 (Add Permissions): +30 minutes
- Step 7 (STT Integration): +2-4 hours (if using whisper.cpp)
- Step 8 (Model Loading): +1-2 hours
- Step 10 (JNI Integration): +4-8 hours

**Total additional time**: ~10-20 hours

