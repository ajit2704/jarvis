# Block-based workspace — phased implementation

**Goal:** Turn the assistant into a **thinking workspace** (todo, notes, then later rich docs + canvas) with **baby steps**: first todo + notes, integrate LLM, then expand. Keep the app **light** (no heavy new deps; Room/markdown/canvas later).

---

## Current state (as implemented)

- **UI:** Single screen = **document workspace** + mic. No chat; feedback via **Snackbar** ("Added todo", list summary, errors). `ChatViewModel` holds one `Document` (in-memory), `appendBlock` / `toggleTodo` / `getTodoListSummary`.
- **Pipeline:** Voice → STT → tool classifier → arg extractor → `ToolExecutor.execute()` → append block or show list summary; Snackbar for confirmations.
- **Blocks:** `Block.Text`, `Block.Todo`; rendered via `ui/blocks/BlockRenderer`, `TextBlock`, `TodoBlock` (Phase 2).
- **Tools:** `create_note`, `todo_list` (working); `set_reminder`, `canvas`, `draw` not yet wired to blocks.
- **Heavy parts:** Moonshine STT, RunAnywhere LLM; no Room/markdown yet.

---

## Package layout (minimal, aligned with existing)

```
app/src/main/java/com/jarvis/voiceassistant/
├── data/           # existing: Message, AssistantState, ModelLoadState
│                   # add: Document, Block, TodoItem
├── assistant/      # existing: AssistantController, JarvisTools, JarvisSystemPrompt
│                   # add: ToolExecutor (creates blocks from tool + args)
├── ui/             # existing: ChatScreen, ChatViewModel, theme
│                   # add: blocks/ BlockRenderer, TextBlock, TodoBlock (Phase 2)
├── llm/
├── stt/
└── audio/
```

**No new top-level packages.** Blocks live under `ui/` (or `ui/blocks/`) to avoid a separate `blocks/` package until we have more block types.

---

## Phase 1 — Todo + notes in memory + LLM integration

**Goal:** User says "add todo buy milk" or "create a note: meeting at 3" → LLM classifies + extracts args → we **append a block** to an in-memory document and show it in the UI. No persistence, no second screen.

### 1.1 Data model (no Room)

| File | Content |
|------|--------|
| `data/Block.kt` | Sealed class or enum: `Block.Text(content)`, `Block.Todo(items: List<TodoItem>)`. Keep it simple: one TODO block = one checklist (list of items). |
| `data/TodoItem.kt` | `data class TodoItem(val text: String, val completed: Boolean = false)` |
| `data/Document.kt` | `data class Document(val id: String, val title: String, val blocks: MutableList<Block>)` — in-memory only. |

**Block representation:** Use a simple sealed class so we can add LIST, QUOTE, CODE, CANVAS later without changing the pattern.

```kotlin
// Block.kt
sealed class Block {
    data class Text(val content: String) : Block()
    data class Todo(val items: MutableList<TodoItem>) : Block()
}
```

### 1.2 ToolExecutor (assistant layer)

| File | Content |
|------|--------|
| `assistant/ToolExecutor.kt` | `fun execute(tool: String, args: Map<String, Any?>): Result<Block?>` — for `create_note` returns `Block.Text(content)`; for `todo_list` with `item` returns `Block.Todo(mutableListOf(TodoItem(item)))`; for `todo_list` with `action: "list"` returns `null` (no new block; caller can show "here's your list" in chat). Other tools return `null` or a stub block for now. |

No database; executor just **builds** the block. ViewModel holds the document and appends.

### 1.3 ChatViewModel + AssistantController

- **ChatViewModel:** Holds a single `Document` (e.g. "My Workspace") in memory: `MutableStateFlow<Document>`. Exposes `fun appendBlock(block: Block)` and `fun toggleTodo(blockIndex: Int, itemIndex: Int)` for UI.
- **AssistantController:** After classifier + arg extraction, call `ToolExecutor.execute(tool, args)`. If result is a `Block`, call `viewModel.appendBlock(block)` and then `viewModel.addAssistantMessage("Added note.")` or `"Added todo: ..."`. If tool is `todo_list` with action "list", build a short summary from current document and add as assistant message (no new block).

This keeps the existing chat flow; the only new behavior is "append block + confirm message" when tool is create_note / todo_list.

### 1.4 UI — Workspace section on same screen

- **ChatScreen:** Add a small **Workspace** section (e.g. above the message list or in a scrollable column): show `document.blocks` in a `LazyColumn`. Each item: if `Block.Text` show a `Text`; if `Block.Todo` show a list of checkboxes + labels. No new screen, no navigation. Optional: "Workspace" title and a thin divider.

This gives immediate feedback: voice → "Added todo: buy milk" → user sees the new todo in the same screen.

### 1.5 Dependencies

**None.** Use only Compose + existing dependencies.

### Phase 1 checklist

- [x] `data/Block.kt`, `data/TodoItem.kt`, `data/Document.kt`
- [x] `assistant/ToolExecutor.kt` (create_note → Text block; todo_list item → Todo block)
- [x] `ChatViewModel`: document state, `appendBlock`, `toggleTodo`, `getTodoListSummary`
- [x] `AssistantController`: after args, call executor; if block returned, append and set assistant message; "list" → todo summary
- [x] `ChatScreen`: Workspace section with blocks (simple `Text` + `Row(Checkbox, Text)` for todos)

---

## Phase 2 — Block renderer + clearer structure

**Goal:** Extract block UI into reusable components and make the workspace the primary focus (still one screen or simple tab).

### 2.1 Block components

| File | Content |
|------|--------|
| `ui/blocks/BlockRenderer.kt` | `@Composable fun BlockRenderer(block: Block, onTodoToggle: (itemIndex: Int) -> Unit)` — `when(block)` dispatch to TextBlock / TodoBlock. |
| `ui/blocks/TextBlock.kt` | Single block of plain text (e.g. `Text(block.content)`). No markdown yet. |
| `ui/blocks/TodoBlock.kt` | List of `Row(Checkbox, Text)`; on toggle call `onTodoToggle`. |

### 2.2 Optional: Workspace-first layout

- Option A: Keep current layout (Workspace strip above chat).
- Option B: Tab or top bar: "Workspace" | "Chat" — Workspace shows full document; Chat shows messages + mic. For baby steps, Option A is enough.

### 2.3 Dependencies

**None.** Still no markdown library; use `BasicTextField` or `Text` only.

### Phase 2 checklist

- [x] `ui/blocks/BlockRenderer.kt`, `TextBlock.kt`, `TodoBlock.kt`
- [x] ChatScreen uses BlockRenderer for document blocks
- [x] Optional: improve styling (cards, spacing) for blocks — card + surfaceVariant background per block

---

## Phase 3 — Persistence (later)

**Goal:** Don’t lose workspace on app kill.

- Add **Room**: entities `Document`, `Block` (with type + JSON or columns for content), `TodoItem` (or embedded in Block). One document for now is fine.
- ViewModel loads document from DB on init; `appendBlock` / `toggleTodo` write through to Room.
- **Dependency:** `androidx.room:room-*` (Room is relatively light).

---

## Phase 4 — Rich notes + canvas (later)

- **Notes:** Add a simple markdown renderer (e.g. `compose-markdown` or minimal custom) for `Block.Text` so "Key insight..." can use **bold** and lists. Optional; can stay plain text for a long time.
- **Canvas:** Add `Block.Canvas(strokes)` and `ui/blocks/CanvasBlock.kt` with Compose Canvas + pointer input; store strokes in memory (then Room). No new heavy deps.
- **Thought visualization / mind map:** Defer until core blocks and voice flow are solid.

---

## Summary

| Phase | Focus | New packages | New deps |
|-------|--------|--------------|----------|
| 1 | Todo + notes in memory, LLM appends blocks, Workspace strip in ChatScreen | — | None |
| 2 | BlockRenderer, TextBlock, TodoBlock; polish block UI | `ui/blocks/` | None |
| 3 | Persistence | — | Room |
| 4 | Rich text, canvas, etc. | — | Optional markdown; Compose Canvas only for canvas |

**Next step:** Phase 1 and Phase 2 are done. When ready: Phase 3 (Room persistence) or Phase 4 (rich text / canvas).
