# STT Test – Log Analysis

## 1. SurfaceFlinger – "Out of order buffers detected"

```
SurfaceFlinger E  Out of order buffers detected for RequestedLayerState{...MainActivity#275529...}
  producedId=1 frameNumber=361 -> producedId=1 frameNumber=306 (and 307, 308, ...)
```

- **What it is:** The compositor (SurfaceFlinger) is seeing frame buffers in a different order than expected (e.g. frame 361 before 306).
- **Cause:** Usually device/GPU/driver or timing (rapid UI updates, Compose recomposition, or app going to background). Common on some OEMs (e.g. Oppo/OnePlus).
- **Impact:** Cosmetic/compositor warning only. Does **not** break STT or app logic.
- **Action:** None required. Can be ignored unless you see visible glitches.

---

## 2. AudioRecord – stop/release

```
AudioRecord D  call AudioRecordThread pause()
AudioRecord D  stop(13020) done
AudioRecord D  stop(13020): mActive:0
AudioRecord I  ~AudioRecord(13020): mStatus 0
```

- **What it is:** Normal teardown after recording: thread paused, stop completed, handle released, status 0 (success).
- **Impact:** None. Recording is ending correctly.

---

## 3. AudioSystem – "closing unknown input"

```
AudioSystem W  ioConfigChanged() closing unknown input 3518
```

- **What it is:** System audio layer reports an input stream being closed; sometimes logged as "unknown" if the handle was already released.
- **Impact:** Benign. Often seen right after releasing `AudioRecord`.

---

## 4. Moonshine native – transcription failure (real bug)

```
Native W  moonshine-c-api.cpp:253:moonshine_transcribe_without_streaming(): Failed to transcribe without streaming: vector
```

- **What it is:** The Moonshine C++ code failed inside `transcribeWithoutStreaming()`. The message "vector" is likely a truncated C++ error (e.g. `std::exception` or a vector-related assert).
- **Impact:** Transcription never succeeds; user sees "Transcribe failed" and no text.
- **Likely causes:**
  1. **Model architecture mismatch:** We load with `MOONSHINE_MODEL_ARCH_TINY` (0). The sherpa-onnx-moonshine-tiny-en .ort model is a **streaming** tiny model; the native "without streaming" path may expect a non-streaming graph or different tensor layout. Using `MOONSHINE_MODEL_ARCH_TINY_STREAMING` (2) may be required for this .ort bundle.
  2. **Audio length / format:** Native code might require a minimum length (samples), or a specific stride/layout. Very short or empty buffers can trigger vector/assert errors.
  3. **Thread/safety:** Less likely if we call once per recording on a single thread.

---

## 5. VRI/HWUI/BufferQueue – activity visibility

```
VRI[MainActivity] D  visibilityChanged oldVisibility=true newVisibility=false
HWUI D  RenderProxy::destroy ...
BufferQueueProducer D  disconnect ...
VRI[MainActivity] D  onFocusEvent false
```

- **What it is:** Activity went to background (user left the app or switched task).
- **Impact:** None. Normal lifecycle.

---

## Summary

| Log | Severity | Action |
|-----|----------|--------|
| SurfaceFlinger out of order | Low (cosmetic) | Ignore |
| AudioRecord stop/release | Info | None |
| AudioSystem unknown input | Low | None |
| **Moonshine "Failed to transcribe without streaming: vector"** | **High** | **Fix model arch and/or audio validation (see code changes)** |
| VRI/HWUI visibility | Info | None |

The only issue that breaks STT is the Moonshine native failure. Fixes applied in code:

1. **Model arch:** Use `MOONSHINE_MODEL_ARCH_TINY_STREAMING` (2) when loading the sherpa-onnx-moonshine-tiny-en .ort model, so `transcribeWithoutStreaming()` matches the loaded graph. If the app then fails to load the model at startup, try switching back to `MOONSHINE_MODEL_ARCH_TINY` (0) in `STTEngine.kt`.
2. **Audio validation:** Require at least ~1 second of audio (MIN_AUDIO_BYTES) before calling the native API to avoid "vector" errors from empty or too-short input.
