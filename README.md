# Echoguard Android Client

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10.2-02303A.svg?style=flat&logo=gradle&logoColor=white)](https://gradle.org/)
[![Compile SDK](https://img.shields.io/badge/Compile%20SDK-34-blue.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Native Android mobile security client for **Echoguard** — an automated, real-time scam call interception and SMS smishing defense platform. The application intercepts active phone calls, captures hardware audio through a resilient fallback chain, streams low-latency PCM16 chunks over WebSockets to the AI detection backend, and displays an on-screen floating warning overlay with sub-second threat scoring.

> 🖥️ **Companion Backend Server:**  
> The high-performance multi-engine detection backend (FastAPI, Whisper STT, Sarvam AI, Ollama LLM, XGBoost, and Rule Engine) is hosted in: **[Echoguard Server](https://github.com/nithinp1/Ecoguard-server)**.

---

## ⚡ Key Highlights

- **Live Call Audio Interception & Streaming:**
  - Automated call state detection via `CallStateManager` (`TelephonyManager` / `PhoneStateListener`).
  - Native 16 kHz Mono 16-bit PCM streaming to the server's `/audio-stream` WebSocket endpoint.
  - Dynamically configurable packet chunk duration presets (**80ms, 100ms, 200ms, 500ms, 1s, 3s**) for sub-second, ultra-low-latency transcription.
  - Root Mean Square (RMS) speech volume tracking for voice activity monitoring.
- **Resilient Audio Capture Fallback Chain:**
  - Priority chain: `VOICE_CALL` ➔ `VOICE_UPLINK` ➔ `VOICE_DOWNLINK` ➔ `VOICE_COMMUNICATION` ➔ `MIC`.
  - Automated root-assisted grant (`su -c "appops set <pkg> CAPTURE_AUDIO_OUTPUT allow"`) for privileged hardware call audio capture on rooted devices (LineageOS, AOSP, Magisk).
  - Seamless fallback to speakerphone microphone capture on non-rooted devices.
- **In-Call Real-Time Threat Overlay:**
  - System alert window (`TYPE_APPLICATION_OVERLAY`) rendering over active phone calls.
  - Real-time animated risk gauge (0–100) with color-coded severity states:
    - 🟢 **Safe (0–39):** Normal conversation flow.
    - 🟡 **Suspicious (40–69):** Cautionary status with haptic pulse alert.
    - 🔴 **Scam / Fraud (70–100):** High-priority alert banner, audio warning tone, persistent haptic vibration, and reasoning breakdown (e.g., OTP extortion, impersonation, bank freeze threats).
- **Automated SMS Smishing Defense:**
  - Background `SmsReceiver` intercepts incoming SMS messages.
  - Dispatches SMS content and sender metadata to the server `/sms-check` endpoint for instant classification.
  - Categorizes senders (Bank, Government, Courier, Promo, Unknown) and flags malicious links and OTP harvesting attempts.
  - Built-in interactive SMS test sandbox to simulate attacks directly in the app.
- **Evidence Vault & Recording:**
  - Local WAV call recording alongside synchronized JSON telemetry records.
  - In-app evidence player with audio waveform scrubbing and secure log sharing via Android `FileProvider`.
- **Diagnostic Telemetry:**
  - Real-time technical log displaying WebSocket frames, latency, audio packet statistics, and hardware audio source state.
- **Modern Material 3 Design:**
  - Glassmorphic interface with Dark / Light theme toggle and fluid tab navigation (`ViewPager2`).

---

## 🏗️ Architecture & Data Flow

```text
 ┌──────────────────────────────────────────────────────────────────────────────────┐
 │                               Android Client                                     │
 │                                                                                  │
 │  ┌───────────────────────┐         ┌──────────────────────────────────────────┐  │
 │  │   CallStateManager    │ ───────►│          ScamDetectionService            │  │
 │  │ (Telephony Call State)│         │     (Foreground Service + AudioRecord)   │  │
 │  └───────────────────────┘         └────────────────────┬─────────────────────┘  │
 │                                                         │                        │
 │  ┌───────────────────────┐                              │ PCM16 Chunks           │
 │  │      SmsReceiver      │ ─────────────┐               │ (80ms - 3s)            │
 │  │  (Incoming SMS Broadcast)            │               ▼                        │
 │  └───────────────────────┘              │     ┌───────────────────┐              │
 │                                         │     │  OkHttp WebSocket │              │
 │  ┌───────────────────────┐              │     │      Client       │              │
 │  │    OverlayService     │◄─────────────┼─────┴─────────┬─────────┘              │
 │  │ (System Floating View)│  Threat      │ JSON Risk     │                        │
 │  └───────────────────────┘  Alerts      │ Updates       │                        │
 └─────────────────────────────────────────┼───────────────┼────────────────────────┘
                                           │               │
                                           │ SMS Payload   │ Streaming PCM Audio
                                           ▼               ▼
 ┌──────────────────────────────────────────────────────────────────────────────────┐
 │                                Echoguard Server                                   │
 │                           (FastAPI + WebSockets)                                 │
 │                                                                                  │
 │     ┌───────────────────────────────┐       ┌──────────────────────────────┐     │
 │     │      Speech-to-Text Layer     │       │    Hybrid Multi-Engine       │     │
 │     │ (Whisper / Sarvam / Parakeet) │ ────► │  (LLM + XGBoost + Rules)     │     │
 │     └───────────────────────────────┘       └──────────────────────────────┘     │
 └──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎙️ Audio Capture Strategy & Device Compatibility

Capturing bidirectional voice call audio on modern Android is heavily restricted by Google's security architecture:

1. **Privileged Audio Sources:**
   - `MediaRecorder.AudioSource.VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK` require the signature-level permission `android.permission.CAPTURE_AUDIO_OUTPUT`.
   - On rooted devices (Magisk, LineageOS, AOSP), Echoguard automatically executes:
     ```bash
     su -c "appops set com.echoguard.companion CAPTURE_AUDIO_OUTPUT allow"
     ```
     This allows direct digital capture of both sides of the phone call without speakerphone echo or background distortion.
2. **Standard Fallback Chain:**
   - If privileged sources fail to initialize, `ScamDetectionService` iterates down the fallback chain:
     $$\text{VOICE\_CALL} \longrightarrow \text{VOICE\_UPLINK} \longrightarrow \text{VOICE\_DOWNLINK} \longrightarrow \text{VOICE\_COMMUNICATION} \longrightarrow \text{MIC}$$
3. **Manual Microphone Override:**
   - Users on non-rooted devices can toggle **Force Microphone Capture** in the settings, placing calls on speakerphone to capture audio via the device microphone.

---

## 📱 User Interface & Navigation

The application features a 4-tab swipeable interface powered by `ViewPager2`:

| Tab | Screen | Description |
| :---: | :--- | :--- |
| **1** | **Dashboard** | Main operational view featuring the real-time threat gauge, call status, live audio source indicator, server IP configuration, and packet chunk duration slider (80ms–3s). |
| **2** | **History / Logs** | Live technical diagnostic console displaying timestamped WebSocket network frames, round-trip latency, audio packet statistics, and connection state. |
| **3** | **SMS Monitor** | Smishing defense center showing analyzed SMS messages, sender trust categorization, flagged threat indicators, and an interactive payload testing tool. |
| **4** | **Evidence Vault** | Secure audio vault listing saved call recordings (`.wav`), associated risk scores, and transcripts, with built-in audio playback and system export. |

---

## 🛡️ Permissions Reference

| Permission | Manifest Identifier | Purpose |
| :--- | :--- | :--- |
| **Microphone** | `RECORD_AUDIO` | Ingests live call audio for PCM chunk streaming. |
| **Audio Capture** | `CAPTURE_AUDIO_OUTPUT` | Privileged hardware call audio recording (granted via Root/AppOps). |
| **Phone State** | `READ_PHONE_STATE` | Detects incoming, outgoing, and terminated call states. |
| **SMS Ingestion** | `RECEIVE_SMS` | Captures incoming SMS texts for instant smishing evaluation. |
| **System Overlay** | `SYSTEM_ALERT_WINDOW` | Displays the floating threat alert window during active phone calls. |
| **Foreground Service** | `FOREGROUND_SERVICE` + `_MICROPHONE` | Keeps the audio recording worker alive during background calls. |
| **Notifications** | `POST_NOTIFICATIONS` | Displays continuous service status and critical scam notifications. |
| **Haptics** | `VIBRATE` | Drives urgency and alert feedback patterns when threats are detected. |

---

## 🚀 Quickstart & Setup

### Prerequisites
- Android device running **Android 8.0 (API 26)** or higher (Root recommended for direct call capture).
- [Android Studio Jellyfish | 2023.3.1+](https://developer.android.com/studio) or newer.
- **JDK 17** or **JDK 21**.
- Running instance of [Echoguard Server](https://github.com/nithinp1/Ecoguard-server).

### 1. Clone the Repository
```bash
git clone https://github.com/nithinp1/Echoguard-companion-app.git
cd Echoguard-companion-app
```

### 2. Build the Debug APK
Using Gradle wrapper:

```bash
# On Linux / macOS
./gradlew assembleDebug

# On Windows
.\gradlew.bat assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install via ADB
Connect your test device via USB with USB Debugging enabled:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Configuration & First Launch
1. **Onboarding:** Open the app; the onboarding wizard will guide you through granting permissions.
2. **Root Grant (Optional):** If prompted by Magisk, tap **Grant** to enable direct hardware call audio capture.
3. **Configure Server IP:**
   - On the **Dashboard**, tap **Server IP**.
   - Enter your Echoguard server WebSocket URL (e.g., `ws://192.168.1.100:8000/audio-stream` or your Tailscale / LAN IP).
4. **Set Packet Duration:**
   - Tap **Package Time** and select your desired streaming chunk duration (recommended: `100ms` or `200ms` for streaming STT; `1s` for Whisper batch mode).
5. **Start Monitoring:** Toggle **Call Monitoring Active**.

---

## 🔌 Communication Protocols

### 1. Streaming Call Audio (`/audio-stream`)
- **Protocol:** WebSocket (Binary PCM16 frames)
- **Audio Format:** 16,000 Hz sample rate, 16-bit Mono Little-Endian PCM with 44-byte WAV header.
- **Client End Frame:** Text message `"END"` sent upon call hangup.
- **Server Score Response:**
  ```json
  {
    "risk_score": 85,
    "reason": "Impersonating law enforcement officer threatening arrest over pending digital warrant",
    "flags": ["police_impersonation", "coercive_urgency", "asset_seizure"],
    "transcript": "This is Officer Sharma from Delhi Police Cyber Cell. A digital arrest warrant has been issued..."
  }
  ```

### 2. SMS Smishing Inspection (`/sms-check` & `/api/sms-check`)
- **Protocol:** WebSocket / HTTP POST JSON
- **Request Payload:**
  ```json
  {
    "sender": "+919876543210",
    "body": "Dear customer, your bank account has been suspended due to pending KYC. Click http://hdfc-verify-kyc.com to update."
  }
  ```
- **Response:**
  ```json
  {
    "risk_score": 92,
    "reason": "Phishing link detected pretending to be bank KYC update.",
    "flags": ["fake_bank_domain", "kyc_suspension", "credential_harvesting"],
    "category": "BANK_FRAUD"
  }
  ```

---

## 📁 Repository Structure

```text
.
├── app/
│   ├── build.gradle                            # Application build configuration & dependencies
│   └── src/main/
│       ├── AndroidManifest.xml                 # App permissions, activities, services, receivers
│       ├── java/com/echoguard/companion/
│       │   ├── MainActivity.kt                 # Host activity, theme manager & ViewPager2 navigation
│       │   ├── DashboardFragment.kt            # Threat gauge, server config & packet duration controls
│       │   ├── ScamDetectionService.kt         # Audio capture worker, WebSocket streaming & alerts
│       │   ├── CallStateManager.kt             # Telephony listener triggering capture on active calls
│       │   ├── OverlayService.kt               # Floating in-call alert overlay with threat indicators
│       │   ├── SmsReceiver.kt                  # Broadcast receiver capturing incoming SMS texts
│       │   ├── SmsAlertsFragment.kt            # SMS threat monitor & interactive test sandbox
│       │   ├── SmsAlertsActivity.kt            # Dedicated SMS alert activity container
│       │   ├── EvidenceRecorder.kt             # Local WAV audio & metadata evidence writer
│       │   ├── EvidenceLogFragment.kt          # Audio evidence vault & playback interface
│       │   ├── EvidenceLogActivity.kt          # Standalone evidence activity
│       │   ├── TechnicalLogFragment.kt         # Diagnostics console & network telemetry
│       │   ├── TechnicalLogActivity.kt         # Standalone technical log activity
│       │   ├── OnboardingActivity.kt           # First-run permission onboarding & root check
│       │   ├── RootHelper.kt                   # Root execution utility for appops permission grant
│       │   ├── DesignComponents.kt             # Reusable UI component builder
│       │   ├── DesignTheme.kt                  # Material 3 & glassmorphic styling tokens
│       │   └── ViewPagerAdapter.kt             # Fragment adapter for 4-tab navigation
│       └── res/                                # Layouts, drawables, colors, and themes
├── gradle/wrapper/                             # Gradle wrapper binaries & properties (Gradle 8.10.2)
├── .gitignore                                  # Comprehensive Android Git ignore rules
├── build.gradle                                # Root project build script
├── gradle.properties                           # Portable Gradle daemon & JVM configuration
├── gradlew                                     # Linux / macOS Gradle wrapper script
├── gradlew.bat                                 # Windows Gradle wrapper script
├── settings.gradle                             # Project settings & repository resolution
└── README.md                                   # Project documentation
```

---

## 🔗 Related Repositories

- **Backend Detection Server:** [Echoguard Server](https://github.com/nithinp1/Ecoguard-server) — Multi-engine streaming backend powering STT transcription, LLM reasoning, and real-time fraud classification.

---

## ⚖️ Privacy, Security & Compliance Notice

- **On-Device Interception:** All call and SMS data is intercepted strictly for security analysis.
- **Data Protection:** Call audio streamed to the backend is processed in memory and not retained unless explicitly saved to the local Evidence Vault on the user's device.
- **Wiretapping & Recording Regulations:** In accordance with telecommunications privacy laws in various jurisdictions, users are responsible for ensuring two-party consent where legally required prior to monitoring or recording telephone conversations.
