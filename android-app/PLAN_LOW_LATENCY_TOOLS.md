# Plan: Low-latency tool flow (classifier → arg extraction)

**Goal:** Reduce latency 50–70% by generating ~1 token for tool choice, then ~5 for args, instead of ~15–25 for full JSON. Model: Qwen 1.5B.

**Current:** One LLM call → full JSON `{"tool":"...","args":{...}}` (~15–25 tokens, ~1–2 s).

**Target:** Two LLM calls (or classifier + rules): tool name only (~1 token, ~100–200 ms) then args (~5 tokens, ~200–400 ms). Optional regex router to skip LLM for common phrases.

---

## Architecture

```
Voice → STT → [Intent router (optional)] → Tool classifier (LLM) → Arg extractor (LLM or rules) → Execute → Chat
```

- **Intent router:** Regex/keywords for "add todo", "create note", "set alarm", etc. If match → known tool + simple arg extraction (or rules). No LLM.
- **Tool classifier:** Single LLM call; prompt asks for one of: `create_note`, `todo_list`, `set_reminder`, `canvas`, `draw`. Max tokens = 5, expect 1 token.
- **Arg extractor:** Second LLM call with tool-specific prompt ("Extract args for todo_list. User: ... Return JSON: {...}") or rule-based extraction for simple patterns.
- **Execute:** Stub for now (or real impl); result shown in chat.

---

## Phase 1: Two-step LLM (classifier + arg extraction)

### 1.1 Tool list and constants

- **File:** `assistant/JarvisTools.kt` (new)
  - `VALID_TOOLS = listOf("create_note", "todo_list", "set_reminder", "canvas", "draw")`
  - `TOOL_DISPLAY_NAMES`: short descriptions for prompts
  - Optional: `toolRequiresArgs(tool: String): Boolean` (e.g. `canvas` can have `{}`)

### 1.2 Tool-classifier prompt

- **File:** `assistant/JarvisSystemPrompt.kt` or new `assistant/Prompts.kt`
  - Add `TOOL_CLASSIFIER_SYSTEM`: "You are Jarvis. Choose the best tool for the user request. Tools: create_note, todo_list, set_reminder, canvas, draw. Respond with ONLY the tool name, nothing else."
  - Add `buildClassifierPrompt(userText: String)`: `<system>...</system>\n<user>\n{userText}\n</user>\n<assistant>\n`
  - Classifier call: `llmEngine.generate(classifierPrompt, maxTokens = 5)`

### 1.3 Parse classifier output

- **File:** `assistant/ToolClassifier.kt` or inside `AssistantController`
  - `parseToolFromOutput(output: String): String?`: strip whitespace, lowercase; if output is in `VALID_TOOLS` return it, else try to match first word/fragment to a tool (e.g. "todo" → todo_list). Return null if no match.

### 1.4 Arg-extractor prompts (per tool)

- **File:** `assistant/Prompts.kt` or `JarvisSystemPrompt.kt`
  - For each tool, define "Extract arguments for {tool}. User said: {userText}. Return ONLY valid JSON with keys: ..."
  - create_note: `{"content": "..."}`
  - todo_list: `{"item": "..."}` or `{"action": "list"}`
  - set_reminder: `{"time": "...", "message": "...", "day": "..."}` (message/day optional)
  - canvas: `{}` or `{"name": "..."}`
  - draw: `{"description": "..."}`
  - `buildArgExtractorPrompt(tool: String, userText: String): String`
  - Arg call: `llmEngine.generate(argPrompt, maxTokens = 30)`

### 1.5 Parse args from output

- **File:** Reuse or port logic from `test-harness/jarvis-eval/eval.py` (`_get_json_from_output`, `parse_output` for args only)
  - `parseArgsFromOutput(output: String, tool: String): Map<String, Any>?`: extract first JSON object, validate keys for tool. Return null on failure.

### 1.6 AssistantController flow (Phase 1)

- **File:** `assistant/AssistantController.kt`
  - After STT and `addUserMessage(transcript)`:
    1. `viewModel.updateState(AssistantState.ProcessingLLM)` (or "Choosing tool…")
    2. `classifierPrompt = buildClassifierPrompt(transcript)` → `llmEngine.generate(classifierPrompt, maxTokens = 5)`
    3. On failure: show error, return
    4. `tool = parseToolFromOutput(classifierResult)`; if null, show "Could not determine action", optional fallback to single full-JSON call
    5. If tool needs no args (e.g. canvas with `{}`), skip to step 7
    6. `argPrompt = buildArgExtractorPrompt(tool, transcript)` → `llmEngine.generate(argPrompt, maxTokens = 30)` → `args = parseArgsFromOutput(argResult, tool)`
    7. Build final message for chat: e.g. `"Tool: $tool\nArgs: $args"` or `{"tool":tool,"args":args}` string, or call `executeTool(tool, args)` and show result
    8. `viewModel.addAssistantMessage(finalMessage)` → `AssistantState.Idle`

### 1.7 Chat UI

- No change: still show user message and one assistant message (the final tool + args or execution result).

### 1.8 LLMEngine

- No API change; keep `generate(prompt, maxTokens)`. Use low `maxTokens` for classifier (5) and arg extractor (30).

---

## Phase 2: Optional intent router (regex)

### 2.1 Intent router

- **File:** `assistant/IntentRouter.kt` (new)
  - Patterns: e.g. `"add todo (.+)"` → tool `todo_list`, arg `item` = group 1; `"create note (.+)"` → `create_note`, `content`; `"set alarm .*"` / `"remind me .*"` → `set_reminder` + simple time/message extraction (regex or very short LLM).
  - `route(transcript: String): Pair<String, Map<String, Any>>?` — if match return (tool, args), else null.

### 2.2 AssistantController (Phase 2)

- After STT, before classifier:
  - `val routed = intentRouter.route(transcript)`
  - If `routed != null`: skip LLM, use `routed.first` and `routed.second`, go to "build final message / execute".
  - Else: run Phase 1 classifier + arg extractor.

---

## Phase 3: Tool execution and UX

### 3.1 Tool execution stub

- **File:** `assistant/ToolExecutor.kt` (new)
  - `execute(tool: String, args: Map<String, Any>): Result<String>` — for now return success message string (e.g. "Added to list: call mom") or stub "Todo added." / "Note saved." / "Reminder set."
  - Later: wire to real note/todo/reminder/canvas/draw handlers.

### 3.2 AssistantController (Phase 3)

- After args are ready: `executor.execute(tool, args)` → show returned string (or error) in chat instead of raw JSON.

---

## Phase 4: Eval harness alignment

### 4.1 Classifier eval

- **File:** `test-harness/jarvis-eval/eval_classifier.py` or extend `eval.py`
  - Reuse `dataset.TESTS`; for each test run classifier prompt only.
  - Metric: accuracy of predicted tool vs `test["tool"]`.
  - Prompt: same as app `TOOL_CLASSIFIER_SYSTEM` + user text.

### 4.2 Arg extractor eval

- For each test: given gold tool, run arg-extractor prompt; compare parsed args to `test["args"]` (key/value normalization as in current eval).

### 4.3 Prompts in sync

- Keep `prompts.py` (or new `prompts_classifier.py`, `prompts_args.py`) so prompts match app constants (JarvisSystemPrompt / Prompts.kt).

---

## File checklist

| Item | Action |
|------|--------|
| `JarvisTools.kt` | New: VALID_TOOLS, tool metadata |
| `Prompts.kt` or `JarvisSystemPrompt.kt` | Add classifier + arg-extractor prompt builders |
| `ToolClassifier.kt` or in controller | parseToolFromOutput |
| Arg parser | parseArgsFromOutput (Kotlin port of eval logic) |
| `AssistantController.kt` | Two-step flow: classifier → arg extractor → message/execute |
| `IntentRouter.kt` | Optional Phase 2: regex router |
| `ToolExecutor.kt` | Optional Phase 3: execute(tool, args) → message |
| `eval_classifier.py` / `eval.py` | Classifier + arg evals |

---

## Latency targets (after Phase 1)

| Stage | Target |
|-------|--------|
| STT | ~400 ms |
| Tool classifier | ~100–200 ms (1–5 tokens) |
| Arg extractor | ~200–400 ms (~5–10 tokens) |
| **Total (LLM)** | **~0.3–0.6 s** (vs ~1–2 s single JSON) |

---

## Optional: logit bias for classifier

Later, if RunAnywhere SDK exposes logit bias / constrained decoding: restrict decoder to only tokens for tool names so classification is effectively one token and more deterministic. Not required for Phase 1.

---

## Order of implementation

1. **Phase 1.1–1.5:** Prompts + parser (classifier + args), no controller change.
2. **Phase 1.6:** Wire two-step flow in AssistantController; show tool + args in chat.
3. **Phase 4:** Eval for classifier and args.
4. **Phase 2:** Intent router; skip LLM when regex matches.
5. **Phase 3:** ToolExecutor and user-facing success messages.
