# Architecture Design

## Component Overview

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ ChatScreen   │  │ ChatViewModel│  │ MainActivity │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
└─────────┼─────────────────┼─────────────────┼──────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────┐
│              Assistant Controller (Orchestrator)          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  AssistantController                               │ │
│  │  - handleUserSpeech(audioData)                     │ │
│  │  - Coordinates: Audio → STT → LLM → UI            │ │
│  └────────────────────────────────────────────────────┘ │
└─────────┬─────────────────┬─────────────────┬──────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────┐ ┌──────────────┐ ┌──────────────┐
│  AudioRecorder   │ │  STTEngine   │ │  LLMEngine   │
│  - record()      │ │  - transcribe│ │  - generate()│
│  - stop()        │ │  - initialize│ │  - initialize │
└─────────────────┘ └──────────────┘ └──────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────┐ ┌──────────────┐ ┌──────────────┐
│  ModelManager   │ │  Moonshine    │ │  RunAnywhere │
│  - download()   │ │  (STT SDK)    │ │  (LLM SDK)    │
│  - load()       │ │               │ │               │
└─────────────────┘ └──────────────┘ └──────────────┘
```

## Data Flow

```
User taps mic
    ↓
AudioRecorder.record() → ByteArray
    ↓
STTEngine.transcribe(ByteArray) → String
    ↓
ChatViewModel.addUserMessage(String)
    ↓
LLMEngine.generate(String) → String
    ↓
ChatViewModel.addAssistantMessage(String)
    ↓
UI updates
```

## Key Principles

1. **Separation of Concerns**: Each component has a single responsibility
2. **Testability**: Use interfaces for dependency injection
3. **Coroutines**: All I/O operations use suspend functions
4. **State Management**: ViewModel holds UI state
5. **Error Handling**: Result types for error propagation

## Component Responsibilities

### ModelManager
- Download model files
- Verify model integrity
- Manage model storage location
- Provide model loading status

### AudioRecorder
- Capture audio from microphone
- Convert to required format
- Handle recording lifecycle
- Provide audio data as ByteArray

### STTEngine
- Initialize Moonshine SDK
- Convert audio to text
- Handle STT errors
- Return transcription result

### LLMEngine
- Initialize llama.cpp/RunAnywhere
- Load GGUF model
- Generate responses
- Handle generation errors

### AssistantController
- Orchestrate the full pipeline
- Coordinate between components
- Update ViewModel state
- Handle errors gracefully

### ChatViewModel
- Manage chat messages
- Track UI state (listening, processing)
- Expose state via StateFlow
- Handle user interactions

