# LLM native crash analysis (SIGABRT in generation)

## What happened

- **Signal:** `Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 10785 (DefaultDispatch), pid 10712`
- **When:** Right after "llm.generation.started" telemetry; during the first LLM generate call.
- **Where:** Native code inside the RunAnywhere SDK, not in our app code.

## Call stack (crash location)

| Frame | Library | Symbol |
|-------|---------|--------|
| #01–#05 | librac_backend_llamacpp.so | (internal) |
| **#06** | **librac_backend_llamacpp.so** | **rac_llm_llamacpp_generate+620** |
| #07 | rac_commons.so | rac_llm_component_generate |
| #08 | runanywhere_jni.so | Java_...RunAnywhereBridge_racLlmComponentGenerate |
| #14 | (APK) | CppBridgeLLM.generate |
| #19 | (APK) | LLMEngine$generate$2.invokeSuspend |

So the process aborts **inside the llama.cpp backend** during `generate()`, after our Kotlin code has called `CppBridgeLLM.generate(prompt, config)`.

## Root cause

- **SIGABRT** is raised by `abort()` in native code — usually an assertion failure, failed sanity check, or explicit error path in the RunAnywhere/llama.cpp backend.
- The crash is **in the SDK’s native library** (`librac_backend_llamacpp.so`), not in our Kotlin or JNI glue. We cannot fix the bug itself in the app.

Possible triggers (for reporting to RunAnywhere):

1. **Prompt length / format** — Our prompt is system + user in ChatML-style (`<system>...</system><user>...</user><assistant>`). The backend may assert on length, token count, or format.
2. **Context / buffer** — Telemetry shows `context_length: 2048`, `max_tokens: 512`. An internal buffer or context check might fail for this combination on Android.
3. **Model/backend state** — Backend might be in an inconsistent state after load (e.g. 1.7B model) and only fails during the first generate.
4. **Thread** — We call generate from `Dispatchers.IO` (DefaultDispatch worker). If the native code assumes a specific thread, that could trigger an assert.

## Telemetry snippet (right before crash)

- Event: `llm.generation.started`
- Model: `SmolLM-1.7B`, `jarvis-llm`
- `context_length`: 2048, `max_tokens`: 512, `temperature`: 0.7
- Telemetry was sent successfully (201) after the crash (async), so the crash is during the actual generation work.

## What we can do in the app

1. **Shorten prompt / reduce max_tokens**  
   Try a smaller system prompt or shorter user text, and/or lower `max_tokens` (e.g. 256) to see if the crash disappears. If it does, report to RunAnywhere with the “working” vs “crashing” config.

2. **Report to RunAnywhere**  
   Open an issue (or use their channel) with:
   - Device: CPH2691, Android 16
   - Model: SmolLM-1.7B (Q4_K_M)
   - Crash: SIGABRT in `rac_llm_llamacpp_generate` (librac_backend_llamacpp.so) during first generate
   - Stack trace (from this log)
   - Prompt format: ChatML-style `<system>...</system><user>...</user><assistant>`
   - context_length 2048, max_tokens 512

3. **Try different model or SDK version**  
   If another GGUF (e.g. SmolLM2-1.7B-Instruct) or a newer RunAnywhere SDK is available, test whether the crash still happens.

## Summary

| Item | Conclusion |
|------|------------|
| Who | RunAnywhere native backend (llama.cpp) |
| Where | `rac_llm_llamacpp_generate` during generate |
| Why | Unknown; likely assert/sanity check or error path in SDK |
| Fix in app? | No; we can only try workarounds (shorter prompt/max_tokens) and report to RunAnywhere |
