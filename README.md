# HeartGuard (智护长庚)

<div align="center">

**AI-powered anti-fraud and health management app for the elderly**
**面向老年人的 AI 防诈与健康管理应用**

</div>

---

## Features / 功能特性

### 🤖 AI Companion Chat / AI 陪聊
- Conversational AI assistant with **voice input** (ASR) and **text input**
- 4 selectable AI personas: Nurse, Tiger Companion, Granddaughter, Volunteer
- Powered by vivo BlueAI Gateway (Chat + ASR + TTS)
- Chat history persisted locally via Room database

---

### 🛡️ Anti-Fraud Drill System / 反诈演练
4 immersive scam scenarios for safety training:
- **Bank impersonation** (voice call drill)
- **Police/fake authority** (voice call drill)
- **AI video deepfake** (video drill with MP4 clips + live camera preview)
- **Relative-in-distress** (voice call drill)

Results (passed/failed) are saved locally for review.

---

### 💊 Medication Reminder / 用药提醒
- Add medications manually, import via **OCR** (ML Kit Chinese text recognition), or use **voice dictation** with AI parsing
- Flexible schedules: daily, every-other-day, custom intervals, one-time
- **AlarmManager**-based scheduling with snooze (15 min delay)
- Home screen **widget** (Glance) showing pending medications & emergency button
- Today's progress tracking per time slot

---

### 🆘 Emergency SOS
- Slide-to-call interface — dials 120 immediately
- Optionally sends **SMS with GPS location** to pre-configured emergency contacts
- Uses `FusedLocationProviderClient` and `SmsManager`

---

### ⚙️ Settings / 设置
- User profile (nickname, avatar photo)
- Font size scaling (4 levels)
- TTS voice settings (volume, speed, tone)
- Emergency contacts editor
- Help guide with FAQ
- Privacy & permissions explanation

---

## Tech Stack / 技术栈

| Category | Libraries |
|----------|-----------|
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose (BOM 2024.10) + Material3 + Navigation Compose 2.8.5 |
| **Widget** | Glance AppWidget 1.1.1 |
| **Architecture** | Single-Activity + MVVM + Repository |
| **Database** | Room 2.6.1 (5 tables, migrations v1→v7) |
| **Network** | OkHttp 4.12 + Retrofit 2.11 + kotlinx-serialization |
| **AI** | vivo BlueAI Gateway (ASR WebSocket / Chat HTTP / TTS WebSocket) |
| **ML** | Google ML Kit Chinese Text Recognition 16.0.1 |
| **Media** | ExoPlayer (Media3) 1.3.1 + CameraX 1.3.4 |
| **Audio** | AudioRecord (PCM 16kHz WAV) + MediaPlayer |
| **Location** | Google Play Services Location 21.0.1 |
| **Image** | Coil Compose 2.7.0 |
| **Async** | Kotlin Coroutines 1.8.1 |
| **Build** | Gradle + Android Gradle Plugin 8.7.3 + KSP |

---

## Architecture / 项目架构

```
┌──────────────────────────────────────────────────┐
│  MainActivity (Single Activity + Splash)         │
│  └─ MainScreen (Scaffold + NavHost + BottomNav)   │
│       ├─ HomeScreen (Chat)                        │
│       ├─ AntiFraudScreen → detail → drill → result│
│       ├─ MedicationListScreen → add/edit/reminder  │
│       ├─ EmergencyScreen (SOS)                    │
│       └─ SettingsScreen (nested sub-screens)       │
├──────────────────────────────────────────────────┤
│  ViewModels                                       │
│  (ChatViewModel / MedicationViewModel /           │
│   FakeCallViewModel / FakeVideoCallViewModel)      │
├──────────────────────────────────────────────────┤
│  Data Layer                                       │
│  ├─ Room Database (AppDatabase + AppDao)          │
│  │   ├─ chat_messages                             │
│  │   ├─ fraud_records                             │
│  │   ├─ medications + medication_schedules +      │
│  │   │   medication_taken_logs                    │
│  └─ Remote (AiGateway → VivoAiRepository)         │
│       ├─ ASR (WebSocket)                          │
│       ├─ Chat (HTTP POST)                         │
│       └─ TTS (WebSocket)                          │
├──────────────────────────────────────────────────┤
│  Utils & Services                                 │
│  ├─ AudioEngine (record / playback)               │
│  ├─ NativeOCRHelper (ML Kit OCR)                  │
│  ├─ SettingsManager (SharedPreferences + Flow)    │
│  ├─ ReminderScheduler (AlarmManager)              │
│  ├─ ReminderAlarmReceiver (BroadcastReceiver)     │
│  └─ HeartGuardWidget (Glance + WidgetReceiver)    │
└──────────────────────────────────────────────────┘
```

---

## Project Structure / 项目结构

```
HeartGuard/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/heartguard/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/         # All Compose screens
│   │   │   │   │   ├── theme/           # Color, Theme, Typography
│   │   │   │   │   └── widget/          # Glance home widget
│   │   │   │   ├── viewmodel/           # 4 ViewModels
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/           # Room DB
│   │   │   │   │   └── remote/          # vivo AI Gateway
│   │   │   │   ├── reminder/            # Alarm scheduling
│   │   │   │   └── utils/               # Audio, OCR, Settings
│   │   │   ├── res/                     # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                        # Unit tests
│   ├── schemas/                         # Room schema exports
│   └── build.gradle.kts
├── gradle/
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties                     # Local SDK path (gitignored)
```

---

## Getting Started / 快速开始

### Prerequisites / 环境要求

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK** 17
- **Android SDK** API 34
- **Gradle** 8.x (wrapped)

### Build / 构建

```bash
# Clone the repository
git clone https://github.com/your-username/HeartGuard.git
cd HeartGuard

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test
```

### Configuration / 配置说明

The app uses **vivo BlueAI Gateway** for AI features. Before running, you must configure API credentials via **one of the following methods**:

| Method | Command |
|--------|---------|
| **Gradle properties** | Add to `gradle.properties`:<br>`VIVO_APP_ID=your_id`<br>`VIVO_APP_KEY=your_key`<br>`VIVO_CHAT_MODEL=Doubao-Seed-2.0-mini` |
| **Environment variables** | `VIVO_APP_ID` / `VIVO_APP_KEY` / `VIVO_CHAT_MODEL` |

> Note: If not configured, the app will build but AI features (chat, ASR, TTS) will fail at runtime.

---

## Minimum Requirements / 最低支持

| | |
|-|-|
| **Min SDK** | 24 (Android 7.0 Nougat) |
| **Target SDK** | 34 |
| **Permissions** | CAMERA, RECORD_AUDIO, CALL_PHONE, SEND_SMS, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM |

---

## License / 许可证

```
MIT License

Copyright (c) 2026 HeartGuard

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files...
```

---

<div align="center">
Built with ❤️ for the elderly community
</div>
