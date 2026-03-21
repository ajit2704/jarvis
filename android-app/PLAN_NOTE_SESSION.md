# Note session — phased plan

Focus: **note flow first**; template selection (note / todo / drawing) later so we **avoid tool-classifier** routing for this path.

---

## Phase 0 — UX & lifecycle (locked)

### Layout (two panes)

| Region | Content |
|--------|--------|
| **Top** | **Processed** — LLM output: concise, reorganized **markdown** (the “polished” note). |
| **Bottom** | **Raw** — live STT text that is **not yet** processed / not yet folded into the markdown layer. |

User always sees **what’s done** above and **what’s still in the queue** below.

### Silence & mic

- **Auto behavior:** Use a **2 minute** silence threshold where relevant (e.g. optional auto-pause or session hints — exact wiring in Phase 1).
- **Mic:** User can close the mic explicitly; capture stops when mic is closed (in addition to any silence rules we implement).

### When does the “flow” stop? (completion)

The note session is considered **complete** only when **both** are true:

1. **Reorganization complete:** Every part of the captured note has been **reorganized into markdown** — i.e. there is **no remaining raw** that still needs to be processed into the top (processed) pane.
2. **Mic closed:** The user has **closed the mic** (recording/listening session ended).

**Implication:** If the user closes the mic while some raw text is still unprocessed, we should run a **final polish pass** (or block “done” until that pass finishes) so condition (1) can be satisfied. Detail this in Phase 2 implementation.

### Out of scope for Phase 0

- Implementation of STT loop, LLM debouncing, markdown renderer — later phases.
- Todo / drawing templates — Phase 4+.

---

## Later phases (preview — not locked)

| Phase | Focus |
|-------|--------|
| **1** | Continuous STT session; raw transcript streaming; 2 min silence; mic open until user stops; **no** tool classifier on this screen. |
| **1b** | **Stabilization layer** (optional but recommended before heavy LLM): history + LCP + pending tail → **stable** text safe for Phase 2. |
| **2** | Periodic LLM: **stable deltas** → processed markdown (top); debouncing; **drain** on silence/mic close + final pass. |
| **3** | Render processed markdown in the top pane. |
| **4** | Template picker (note / todo / drawing) — separate pipelines. |

---

## Phase 1 — Implemented

- **Entry:** `MainActivity` → `NoteSessionScreen` + `NoteSessionViewModel` (STT only; no LLM, no `AssistantController`).
- **Capture loop:** Record **5 s** chunks → Moonshine `transcribe` → append to **raw** string (space-separated segments).
- **Silence auto-stop:** If **no** non-silent audio (PCM RMS below threshold) **and** no transcript text for **2 minutes** since last activity → stop session + Snackbar.
- **Manual stop:** FAB toggles start/stop; `AudioRecorder.stop()` ends the current chunk early.
- **UI:** Top card = Phase 2 placeholder; bottom card = **Raw (live)** transcript.
- **Files:** `NoteSessionScreen.kt`, `NoteSessionViewModel.kt`, `audio/PcmAudioUtils.kt`; `STTEngine.MIN_AUDIO_BYTES` public for chunk gating.

### Phase 1 checklist

- [x] Continuous STT session (chunked loop)
- [x] Raw transcript appended while session active
- [x] 2 min silence threshold (RMS + STT activity)
- [x] Mic until user stops (FAB)
- [x] No tool classifier on this screen

---

## Phase 1 — Low-level architecture (flow + files)

### What is *not* in Phase 1

| Absent | Reason |
|--------|--------|
| `AssistantController`, `JarvisTools`, `JarvisSystemPrompt`, `ToolExecutor` | No tool classification or arg extraction |
| `LLMEngine`, `ModelManager` | No on-device LLM on this screen |
| `ChatScreen`, `ChatViewModel` | Old workspace/assistant entry (not used by `MainActivity` for Phase 1) |

### Layered flow (top → bottom)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  UI (Compose)                                                            │
│  MainActivity.kt → NoteSessionScreen.kt                                  │
│    • Collects: initState, rawTranscript, processedPlaceholder,           │
│      captureState, snackbarMessage                                       │
│    • FAB → startNoteSession() / stopNoteSession()                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Session + state (ViewModel)                                             │
│  NoteSessionViewModel.kt                                                 │
│    • init { }: SttModelManager.ensureModelExists() → STTEngine.init()  │
│    • startNoteSession(): viewModelScope.launch { capture loop }          │
│    • StateFlows: NoteSessionInitState, rawTranscript, captureState, …    │
│    • (Phase 1b) TranscriptStabilizer → committedStable / pendingTranscript│
│    • stopNoteSession(): stopRequested + AudioRecorder.stop()             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
┌──────────────────────────────┐   ┌──────────────────────────────────────┐
│  Audio (PCM capture)         │   │  STT (Moonshine)                     │
│  audio/AudioRecorder.kt      │   │  stt/STTEngine.kt                    │
│    • record(CHUNK_SECONDS)   │   │    • transcribe(ByteArray PCM)       │
│    • stop() → early exit      │   │    • JNI Transcriber (Moonshine)     │
│  audio/PcmAudioUtils.kt        │   │  stt/SttModelManager.kt              │
│    • pcm16MonoRms,           │   │    • ensureModelExists(), model dir  │
│      isLikelySilent()        │   │    • assets / on-disk .ort models    │
└──────────────────────────────┘   └──────────────────────────────────────┘
```

### Capture loop (inside `NoteSessionViewModel`)

1. Set `captureState` → **Listening**.
2. **`audioRecorder.record(5)`** → `ByteArray` PCM (16 kHz mono 16-bit) or early exit if `stop()`.
3. If `stopRequested` (user tapped ⏹), break loop.
4. **`PcmAudioUtils.isLikelySilent(pcm)`** — RMS vs `DEFAULT_SILENCE_RMS` (~350).
5. **`captureState`** → **Transcribing**.
6. If `pcm.size >= STTEngine.MIN_AUDIO_BYTES` (~1 s audio): **`sttEngine.transcribe(pcm)`** → `String`.
7. Update **`lastNonSilentMs`** if RMS not silent **or** transcript non-blank.
8. Append non-empty transcript to **`_rawTranscript`** (space-separated). **Phase 1b:** also push through **`TranscriptStabilizer`** → update **`committedStable`** / **`pendingTranscript`**.
9. If **`now - lastNonSilentMs >= 2 * 60 * 1000`** → **EndedBySilence**, Snackbar, break.
10. Loop to step 1 until stop or silence end.
11. On exit: **EndedByUser** or **Idle** as appropriate; `captureJob = null` in `finally`.

### File map

| File | Role |
|------|------|
| `app/.../MainActivity.kt` | Composes theme + `NoteSessionScreen` + `NoteSessionViewModel` factory |
| `app/.../ui/NoteSessionScreen.kt` | Two-pane UI, FAB, Snackbar, permission for `RECORD_AUDIO` |
| `app/.../ui/NoteSessionViewModel.kt` | Init STT, capture coroutine loop, silence timer, `StateFlow`s |
| `app/.../audio/AudioRecorder.kt` | `AudioRecord` PCM read loop; `stop()` cooperates with `record()` |
| `app/.../audio/PcmAudioUtils.kt` | RMS + silence helper for 2 min rule |
| `app/.../stt/STTEngine.kt` | Moonshine `Transcriber`, `MIN_AUDIO_BYTES` gate |
| `app/.../stt/SttModelManager.kt` | Model path / download / `getModelDir()` |
| `app/.../ui/theme/*` | `JarvisVoiceAssistantTheme` (colors, typography) |

### Threading / runtime

- **ViewModel** runs the capture loop on **`viewModelScope`** (main-immediate dispatcher for launch; `record` / `transcribe` use **IO** inside their implementations).
- **AudioRecorder.record** uses `withContext(Dispatchers.IO)`.
- **STTEngine.transcribe** uses `withContext(Dispatchers.IO)`.

---

## Phase 1b — Stabilization layer (plan)

**Goal:** Fix the gap between **streaming STT** (noisy, repeated, half-words) and **Phase 2 LLM** (needs clean, stable text). Stabilization is **not** semantic rewriting — it’s **consensus + tail-hold** so we don’t feed garbage upstream.

### Problem

| Today (`NoteSessionViewModel`) | Issue |
|-------------------------------|--------|
| `_rawTranscript` = single growing string | Unbounded; duplicates; half words across chunk boundaries |
| LLM (Phase 2) would read the whole thing | Wasted tokens; confusing structure |

### Target pipeline

```text
PCM → STT (Moonshine per chunk)
        ↓
   [optional] rawSegment log (debug / “full bleed”)
        ↓
   ┌─────────────────────────────┐
   │  Stabilization (pure logic)   │
   │  • ring buffer of last K STT  │
   │  • longest common prefix (LCP)│
   │  • hold last N words (tail)  │
   └─────────────────────────────┘
        ↓
stableTranscript  ──────────────► Phase 2 LLM (deltas only)
pendingTranscript ──────────────► UI (unstable tail) + final drain
```

**Rule for Phase 2:** LLM should consume **`stableTranscript` deltas** (or `processedOffset` … `length`), **not** the raw append-only string.

### State model (ViewModel)

| Field | Role |
|-------|------|
| `rawTranscript` | **Optional:** keep as “live log” of concatenated STT segments (debug / optional UI toggle), or replace with `lastRawSegment` only. |
| `transcriptHistory` | `MutableList<String>` or ring buffer, max **K** entries (e.g. K=5): last K chunk transcripts. |
| `committedStable` | String: committed stable prefix (grows monotonically). |
| `pendingTranscript` | Unstable tail (after LCP + optional word-hold). |
| `llmProcessedOffset` | Int: index into `committedStable` already sent to LLM (Phase 2). |

**UI (Phase 0 layout preserved):**

- **Top:** Processed markdown (Phase 2) — unchanged.
- **Bottom:** Show **`committedStable + " " + pendingTranscript`** (or two sub-labels: “Stable” / “Pending”) so users see the tail still moving.

### Algorithm (pure functions → testable)

Suggested file: `note/TranscriptStabilizer.kt` (or `ui/stabilization/TranscriptStabilizer.kt`).

1. **On each non-blank STT string:** `history.add(segment); if (history.size > K) history.removeAt(0)`.
2. **Stable prefix:** `lcp = longestCommonPrefix(history)` (character-wise LCP across all strings in `history`; if length 1, use full string as prefix).
3. **Word hold:** `trimLastWords(lcp, keepUnstableWords = 2)` — never commit the last 1–2 words as “stable” (they change most often).
4. **Commit:** If `trimmed.length > committedStable.length`, append `trimmed.substring(committedStable.length)` to `committedStable`.
5. **Pending:** `pending = latestSegment.removePrefix(committedStable)` or remainder after stable prefix (define one consistent rule; align with latest chunk vs full concat).

**Edge cases (document in code):**

- Empty history / empty STT → no-op.
- LCP shrinks (STT correction) → policy: **never shrink `committedStable`** (monotonic commit); only grow. Divergence lives in `pending` until next chunks stabilize.
- Normalization: trim whitespace; collapse duplicate spaces before LCP if needed.

### Triggers (Phase 2 — reference only)

| Trigger | Action |
|---------|--------|
| Stable delta length ≥ N chars | Queue LLM with `committedStable.substring(llmProcessedOffset)` |
| Silence (already have 2 min / chunk RMS) | Optional **shorter** flush for LLM (e.g. 3–5 s) — separate from session auto-stop |
| Mic `stopNoteSession()` | `finalDrain`: flush pending + remainder into one last LLM pass |

### Separation of concerns

| Layer | Solves |
|-------|--------|
| **STT** | Acoustic → text |
| **Stabilization** | Chunk-to-chunk consensus + safe tail |
| **LLM (Phase 2)** | Markdown / structure / concision |

### Phase 1b implementation checklist

- [x] `note/TextStabilization.kt` — pure LCP + `trimLastWords`
- [x] `note/TranscriptStabilizer.kt` — ring buffer, monotonic commit, `StabilizationSnapshot`
- [x] Unit tests: `TextStabilizationTest`, `TranscriptStabilizerTest`
- [x] `NoteSessionViewModel` — `applyStabilization` per segment; `committedStable`, `pendingTranscript`, `llmProcessedOffset` (0); `resetStabilizer` on session start / clear
- [x] `NoteSessionScreen` — labeled **Stable** / **Pending** + **Raw STT (segments)**

---

## Phase 0 checklist

- [x] Layout: **processed (markdown) on top**, **raw unprocessed below**
- [x] **2 minute** silence threshold accepted
- [x] Session complete when: **all content reorganized into markdown** + **mic closed** (with final-pass behavior TBD in Phase 2)

---

*Next: Phase 2 — LLM on stable deltas + drain on mic close.*

**Production review:** **`STABILIZATION_LLD.md`** — critic’s feedback folded into a **v2** low-level design (word-LCP, cumulative transcript, time gates, LLM triggers).
