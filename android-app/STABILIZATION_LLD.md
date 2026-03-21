# Stabilization layer — low-level design (review)

**Scope:** Note session pipeline: STT → stabilization → (Phase 2) LLM → processed markdown.  
**Code today:** `note/TextStabilization.kt`, `note/TranscriptStabilizer.kt`, `ui/NoteSessionViewModel.kt`.

This document **factors in external code review feedback**: what matches production intent, what will break, and a **v2** target design for sign-off before large refactors.

---

## 1. Verdict (aligned with review)

| Statement | Assessment |
|-------------|------------|
| Separation STT → raw → stabilization → stable → LLM | **Correct** — matches `NoteSessionViewModel` + `TranscriptStabilizer`. |
| History + LCP as baseline | **Correct direction** — implemented in `TranscriptStabilizer.onSegment`. |
| Delta / `processedOffset` for LLM | **Critical** — `_llmProcessedOffset` exists; Phase 2 must send **only** `committedStable.substring(offset)`. |
| Production-ready as-is | **Not yet** — text handling is **character-LCP**; history is **per-chunk strings**, not cumulative; **no time dimension**; **static** tail trim. |

---

## 2. Current implementation map (files)

```
NoteSessionViewModel
  appendRaw(segment)           → _rawTranscript (concat for debug/UI)
  applyStabilization(segment)  → TranscriptStabilizer.onSegment(segment)
                                 → _committedStable, _pendingTranscript

TranscriptStabilizer
  history: ArrayDeque<String>    ← each STT *chunk output* (independent)
  committedStable: String        ← monotonic grow from "candidate"
  onSegment(segment):
    LCP = TextStabilization.longestCommonPrefix(segments)   // CHARACTER-level
    candidate = trimLastWords(LCP, keepUnstableWords)       // static N
    growCommittedMonotonic(candidate)
    pending = extractPending(latestSegment, committedStable)

TextStabilization
  longestCommonPrefix            // character by character
  trimLastWords(text, n)
```

**Phase 2 (not implemented):** LLM triggers; only placeholder `llmProcessedOffset`.

---

## 3. Failure modes (strictly tied to code)

### 3.1 Character-level LCP (`TextStabilization.kt` L19–23)

**Issue:** Partial words and punctuation align by **character**, not **word**.

**Example:**

| Chunk 1 | Chunk 2 | Char LCP (current) | Desired |
|---------|---------|---------------------|---------|
| `buy milk and brea` | `buy milk and bread` | `buy milk and brea` | `buy milk and` (word-safe) |

**Risk:** `committedStable` can end on **`brea`** until the next chunk extends — garbage suffix risk for Phase 2.

**Fix (P1):** Replace pairwise LCP with **word-token LCP** (`lcpWords` / fold over token lists). Keep char-LCP only as fallback for languages without clear word boundaries (optional).

---

### 3.2 Independent chunk history (`TranscriptStabilizer.kt` L54–58)

**Issue:** `history` stores **each chunk in isolation** (`history.addLast(trimmed)` where `trimmed` = that chunk’s STT only).

**Example:**

| t1 | t2 | Char LCP(t1,t2) |
|----|----|-----------------|
| `buy milk` | `milk and bread` | `m` or short junk — **not** aligned to full note |

Moonshine returns **chunk-local** text; boundaries shift. LCP across **unrelated** prefixes collapses.

**Fix (P1):** Maintain **`runningTranscript`** (cumulative):

```text
runningTranscript += " " + newChunk   // normalized spacing
history.add(runningTranscriptSnapshot)  // last K *full* snapshots
LCP = wordLCP across history list
```

**ViewModel change:** pass **cumulative string** into stabilizer (or stabilizer owns `runningTranscript` internally).

**Implementation note:** Once `history` holds **nested** cumulative snapshots (`s₁`, `s₁+s₂`, `s₁+s₂+s₃`, …), **word-LCP across the whole list equals the shortest snapshot** (usually **only the first chunk**). That makes `committedStable` look like “first sentence” forever. The implemented fix is: **candidate = `trimLastWords(runningTranscript, keepUnstableWords)`** + monotonic grow. Keep **`longestCommonPrefixWords`** in `TextStabilization` for **alternative** full strings (e.g. two hypotheses for the same span), not for nested running history.

---

### 3.3 No time-based stabilization

**Issue:** Commits are **purely text**. Rapid STT updates can flip LCP every chunk → unstable UX and duplicate LLM calls in Phase 2.

**Fix (P2):** Introduce **`TranscriptFrame(text, timestampMs)`** or “first seen” per stable prefix segment:

- Do not extend `committedStable` until `now - firstSeenStableTime >= T_commit` (e.g. 300–500 ms), **or** silence boundary (already have chunk-level silence in capture loop — may **reuse** or add finer timer).

---

### 3.4 Static `trimLastWords(..., 2)` (`TranscriptStabilizerConfig`)

**Issue:** Fixed **2** words is wrong for fast dictation vs slow speech.

**Fix (P2/P3):**

- **Simple:** `keepUnstableWords = f(chunkDurationMs)` or RMS “speech rate” proxy.
- **Better:** approximate **~300–500 ms of audio** as uncommitted tail (needs word timestamps from STT — **not** available today → defer).

---

### 3.5 Phase 2 LLM triggers (not implemented — design only)

**Issue:** Naive `length > 50` cuts mid-sentence and breaks markdown.

**Fix (Phase 2 spec):**

| Trigger | Condition |
|---------|-----------|
| Sentence boundary | Regex / `\.` `\n` `?` `!` after stable text |
| Silence | Reuse session silence or shorter **flush** window (3–5 s no new stable) |
| Timeout | No new stable text for N seconds |
| Mic stop | `finalDrain`: `committed + pending` → one LLM pass |

**Delta send:**

```kotlin
val delta = committedStable.substring(llmProcessedOffset)
if (shouldTriggerLLM(delta, context)) { send(delta); llmProcessedOffset = committedStable.length }
```

---

## 4. Target architecture (v2) — low-level

### 4.1 Data flow

```text
                    ┌─────────────────────────────────────┐
                    │  NoteSessionViewModel               │
  PCM → STT chunk   │  runningTranscript += chunk         │
        │             │         │                           │
        └─────────────┼────────►│  StabilizerEngine v2      │
                      │         │  • history: K snapshots   │
                      │         │    of runningTranscript   │
                      │         │  • word-LCP → candidate   │
                      │         │  • time-gated commit      │
                      │         │  • dynamic tail trim      │
                      │         └──► committedStable        │
                      │              pending               │
                      └────────────────┬──────────────────┘
                                       │
                         Phase 2 ───────► LLM (delta only)
```

### 4.2 State (v2)

| Field | Owner | Meaning |
|-------|--------|---------|
| `runningTranscript` | VM or stabilizer | Cumulative text (normalized spaces). |
| `historySnapshots` | Stabilizer | Last K strings = snapshots of `runningTranscript` **after** each update. |
| `committedStable` | Stabilizer | Monotonic; word-safe; optionally time-gated. |
| `pending` | Derived | `runningTranscript.removePrefix(committedStable)` (with trim rules). |
| `llmProcessedOffset` | VM | Index into `committedStable` for deltas. |
| `rawConcat` | VM | Optional debug log (current `_rawTranscript`). |

### 4.3 Algorithm sketch (v2 `onChunk(newChunk)`)

1. `running = normalize(running + " " + newChunk)`.
2. `snapshots.add(running)`; trim ring to K.
3. `candidateWords = wordLCP(snapshots)`.
4. `candidate = trimDynamicTail(candidateWords)` (words / time / heuristics).
5. **Time gate:** if `candidate` extended prefix vs `committed`, require **min dwell** OR silence before commit.
6. `committed += delta` (monotonic); `pending = running.removePrefix(committed)`.
7. Emit snapshot for UI + enqueue Phase 2 delta policy.

### 4.4 What stays modular

- **`TextStabilization`** — split into `WordStabilization` (word LCP, normalize) + keep `trimLastWords` with dynamic params.
- **`TranscriptStabilizer`** — replace internals; **same package**; VM API can stay (`onSegment` → `onChunk` + cumulative contract).
- **Phase 2** — new `NoteLlmController` or methods on VM: `shouldTriggerLLM`, `consumeDelta` — **no** STT in that class.

---

## 5. Priority backlog (for implementation tickets)

### P1 — Must (stability correctness)

- [x] **Word-level LCP** helper (`longestCommonPrefixWords`) for alternative hypotheses / tests — **not** applied to nested cumulative history (see §3.2 implementation note).
- [x] **Cumulative `runningTranscript`** (append chunks; stabilizer-owned).
- [x] **Delta commit** — `trimLastWords(running, keepUnstable)` + monotonic grow; unit tests for `brea` / `bread` on word LCP.

### P2 — High impact

- [ ] **Time-based commit** (300–500 ms dwell) or debounce before extending `committedStable`.
- [ ] **Dynamic** `keepUnstableWords` or minimum tail length heuristic.
- [ ] **Phase 2:** `shouldTriggerLLM`: sentence boundary + silence + timeout (no raw length-only).

### P3 — Next level

- [ ] STT **confidence** per token (if Moonshine exposes later) → confidence-gated commit.
- [ ] Word timestamps → **~500 ms audio** uncommitted tail.

---

## 6. Insight captured (reviewer)

> Stabilization = **text + time + audio context** (we have text + partial time via chunk loop; full **audio context** needs VAD/word timing — P3).

---

## 7. References in repo

| Document / code | Role |
|-----------------|------|
| `PLAN_NOTE_SESSION.md` | Product phases + Phase 1b checklist |
| `note/TextStabilization.kt` | **Word LCP** (`longestCommonPrefixWords`) + char LCP retained for legacy/tests |
| `note/TranscriptStabilizer.kt` | **Cumulative** `runningTranscript`; stable candidate = **trim unstable tail** + monotonic (no LCP on nested history) |
| `NoteSessionViewModel.kt` | Still calls `onSegment` per STT chunk; stabilizer **accumulates** internally |

---

## 8. Sign-off checklist (reviewers)

- [ ] Word-LCP + cumulative snapshots accepted as **P1** scope.
- [ ] Time-gated commit window (e.g. 300–500 ms) acceptable for **P2**.
- [ ] Phase 2 triggers: sentence + silence + timeout — **no** length-only threshold.
- [ ] `llmProcessedOffset` remains single source of “what LLM already saw” from **committedStable** only.

---

*End of LLD — implementation of P1/P2 should be tracked as separate PRs after approval.*
