# LLM model in assets and build / -422

## Truncated download → -422

If the model file is **incomplete** (e.g. only 408 MB instead of ~1.6–2.1 GB), llama.cpp returns **-422** (GGUF header says tensor blocks are missing). The app now:

- **Validates size** before marking the model ready: file must be ≥ 1.4 GB (covers Q4 ~1.6 GB and Q5 ~2.1 GB).
- **Deletes truncated cache** and re-downloads or re-copies.
- **Validates download**: if `Content-Length` is present, checks `totalRead >= contentLength` after download.

**Why 3GB+ RAM?**  
llama.cpp needs more than the file size at runtime: **model weights** (~1.6 GB for Q4) + **KV cache** (~1 GB for n_ctx=4096) + **buffers** (~300–500 MB) ≈ **3–3.2 GB**. On Android, app RAM is limited (e.g. ~2.5–3 GB on an 8 GB device), so the loader can fail or warn. **Fix:** use **n_ctx = 1024** (or 768) so KV cache drops to ~250 MB and total RAM ≈ **2.1 GB**. The app requests this via `ModelConfig(contextLength = 1024, ...)`; the RunAnywhere SDK does not yet pass config to the native loader, so until it does, -422/OOM may still occur. Also **use_mmap = false** on Android reduces fragmentation; we pass it but the SDK may not forward it.

---

**Do we store the model in RAM?**  
No. The GGUF is stored **on disk** only (`files/runanywhere/SmolLM3-3B-Q4_K_M.gguf`). It is **not** loaded into RAM until the RunAnywhere/llama.cpp native loader runs. That loader typically uses **memory-mapping (mmap)** so the OS can page in parts of the file as needed. On Android, mmap of large files can fail (error **-422**) due to device limits or kernel behavior. Loading the model **fully into RAM** (no-mmap) would avoid that but requires the RunAnywhere native layer to support a no-mmap option; the Kotlin SDK has `ModelConfig(useMemoryMap = false)` but the current version does not pass it to the native call. If you see -422 with a **complete** file (~1.5–2 GB), try closing other apps to free RAM, or open an issue with [RunanywhereAI/runanywhere-sdks](https://github.com/RunanywhereAI/runanywhere-sdks) asking for no-mmap / load-in-RAM on Android.

---

**To remove a broken model and retry:**

```bash
# Current default: SmolLM-1.7B-Instruct Q4_K_M
adb shell run-as com.jarvis.voiceassistant rm -f files/models/SmolLM-1.7B-Instruct.Q4_K_M.gguf files/runanywhere/SmolLM-1.7B-Instruct.Q4_K_M.gguf
# Previous 3B models (if you had them)
adb shell run-as com.jarvis.voiceassistant rm -f files/models/SmolLM3-3B-Q4_K_M.gguf files/runanywhere/SmolLM3-3B-Q4_K_M.gguf
adb shell run-as com.jarvis.voiceassistant rm -f files/models/SmolLM3-3B-Q5_K_M.gguf files/runanywhere/SmolLM3-3B-Q5_K_M.gguf
```

Then reopen the app to trigger a fresh download.

---

## Debugging -422 in logcat

System logs (sensors, charger, Osense, WifiHAL, etc.) will flood the buffer. To see **our** load attempt:

```bash
adb logcat -s JarvisLLM:E
# or by package:
adb logcat --pid=$(adb shell pidof -s com.jarvis.voiceassistant)
```

On load failure the app logs: `loadModel failed: code=<result> path=<path> size=<bytes> canRead=<true|false>`. Confirm path is under the app's `filesDir` and size is ~1.5–2 GB. **-422 with a complete file** is usually the native loader (e.g. mmap on Android). Workarounds: close other apps, try a smaller model (e.g. 1.5B GGUF), or request config support in [RunanywhereAI/runanywhere-sdks](https://github.com/RunanywhereAI/runanywhere-sdks).

---

## What’s going on

1. **Error -422**  
   The log shows:
   - `Loading model: jarvis-llm from .../files/models/SmolLM3-3B-Q5_K_M.gguf`
   - `rac_llm_component_load_model returned: -422`  
   So the app is already using a path under `files/models/`. -422 can mean the file was missing, corrupted, or the native loader failed for another reason (e.g. device memory or GGUF format).

2. **Asset in `app/src/main/assets/smolm-3b`**  
   You added the GGUF under `assets/smolm-3b/`. The app is written to **prefer copying from that asset** to `filesDir/models/` and then loading from there. So if the asset were present in the APK, it would be used and avoid download.

3. **Build failure with the 2GB asset**  
   If the 2.1 GB file stays in `src/main/assets/smolm-3b/`, the Android build fails at `compressDebugAssets` with **"Required array size too large"**. The build pipeline tries to process that file and runs out of memory. So you **cannot** ship this APK with the GGUF inside the default assets and still have the build succeed.

## What to do

### Option A: Build without the asset (recommended)

1. **Move the GGUF out of assets** so the build no longer sees it, for example:
   - Move `app/src/main/assets/smolm-3b/` to somewhere outside the app (e.g. project root or a separate “large-assets” folder) and **don’t** put it back under `src/main/assets/`, or  
   - Delete it from the repo and add `app/src/main/assets/smolm-3b/` (or the big file) to `.gitignore` if you only need it locally.

2. **Build the app**  
   With the large file no longer under `src/main/assets/`, `assembleDebug` should succeed.

3. **Get the model on the device**
   - **In-app download:** Run the app and let it download the model (ModelManager will fall back to the URL).  
   - **Manual copy:** After install, copy `SmolLM3-3B-Q5_K_M.gguf` into the app’s files dir, e.g.:
     - `adb push SmolLM3-3B-Q5_K_M.gguf /sdcard/Android/data/com.jarvis.voiceassistant/files/models/`  
     (or use a file manager with access to that path).  
     The app expects the file at:  
     `context.filesDir/models/SmolLM3-3B-Q5_K_M.gguf`.

4. **If you still see -422**  
   Then the problem is likely not “download vs asset”, but one of:
   - Corrupt or wrong GGUF (try a fresh download or a known-good file).  
   - RunAnywhere/llama.cpp or device limits (memory, format, etc.). On Android, -422 is often **mmap-related**; try a smaller GGUF (e.g. SmolLM2 360M) or check [RunanywhereAI/runanywhere-sdks](https://github.com/RunanywhereAI/runanywhere-sdks) for "-422" / "Android load".

### Option B: Keep the asset only for a custom build

If you want to keep the file under `assets/smolm-3b/` for a **different** build (e.g. one that doesn’t run the default compress step, or a custom pipeline), you can, but that build cannot use the default `assembleDebug` path that runs `compressDebugAssets` on all assets. The app code is already set up to use the asset when it’s present at runtime (`assets/smolm-3b/SmolLM3-3B-Q5_K_M.gguf` → copy to `filesDir` → load).

## Summary

- **Is it a “download issue”?**  
  Partly: the app was loading from `files/models/` and got -422. That can be due to a missing/bad file (e.g. from a failed or partial download). Using a good file (from assets copy or manual push or a successful in-app download) may fix it.
- **Asset in `assets/smolm-3b`:**  
  Supported at runtime, but having the 2GB file there breaks the default build. Move it out of `src/main/assets/` to build, then use download or manual copy to get the model on device.
