# Jarvis Voice Assistant - Android App

Voice-to-text assistant using Moonshine STT and SmolLM3-3B LLM.

## Project Structure

```
app/src/main/java/com/jarvis/voiceassistant/
├── MainActivity.kt          # Main entry point
├── ui/                      # UI components
│   ├── ChatScreen.kt       # Chat interface
│   └── theme/              # Material Design theme
├── audio/                   # Audio recording
├── stt/                     # Speech-to-text (Moonshine)
├── llm/                     # LLM inference (SmolLM3)
└── assistant/               # Assistant controller
```

## Dependencies

- **Moonshine STT**: `ai.moonshine:moonshine-voice:0.0.48`
- **LLM Runtime**: `ai.runanywhere:runanywhere-llamacpp:0.16.0-test.39`
- **Jetpack Compose**: Material 3
- **Kotlin Coroutines**: For async operations

## Setup

1. Open in Android Studio
2. Sync Gradle files
3. Build and run on device/emulator

## Requirements

- Android Studio Hedgehog or later
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)

## Development Status

- ✅ Step 0: Pre-setup (Complete)
- ✅ Step 1: Project setup (Complete)
- ⏳ Step 2-10: In progress

