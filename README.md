# Safenex-Band-BLE-Integration

Personal Emergency Safety System with Bluetooth Low Energy (BLE) Wearable Band Integration, Android Application, and Progressive Web App.

---

## 🛡️ Overview

**SAFENEX** is a mission-critical personal safety and rapid emergency response ecosystem designed to protect users during transit, commutes, and high-risk situations. It connects to the custom **SAFENEX-BAND** ESP32 wearable hardware over Bluetooth Low Energy, featuring multi-modal deterrents, real-time cloud tracking, and AI voice assistance.

---

## ✨ Key Features

### 1. 🔴 Top Bright Red Round SOS Emergency Button
- High-visibility circular emergency trigger with animated multi-layer neon-red pulsing halos.
- One-tap immediate escalation into full emergency protocol.

### 2. 📡 ESP32 Wearable SAFENEX-BAND BLE Integration
- Background Bluetooth Low Energy (BLE) peripheral scanning and GATT connection.
- BLE Service: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- Hardware SOS trigger notifications, battery telemetry, and continuous heartbeat monitoring.

### 3. 🚨 Non-Stop Acoustic Siren & Camera Strobe Deterrents
- **1200Hz Acoustic Siren**: Synthesizes a loud 800Hz–1300Hz frequency sweeping alarm.
- **Rapid Camera LED Strobe**: 10Hz tactical disorienting light flasher.
- **Power Button Lockdown**: Runs with Android `PARTIAL_WAKE_LOCK` so deterrents never stop even when the screen or power button is pressed.
- **Kiosk Mode**: Back button, Home gestures, and app switcher are blocked until safety PIN `1234` is verified.

### 4. 🕒 Commute Safety Mode (Dead-Man's Arrival Switch)
- Departure and arrival scheduler with quick presets (15m, 30m, 1h, 2h, 8:00 PM).
- **60-Second Overdue Check-In Alert**:
  - `[ ✅ I've Arrived Safely ]`: Cancels alarm and automatically dispatches Safe Arrival SMS to Primary Guardian.
  - `[ ⏳ Extend +15 Mins ]`: Adds extra travel time.
  - `[ 🚨 Trigger SOS Now ]`: Fires full emergency mode.
- **Automatic Timeout**: Automatically escalates to full emergency SOS if unattended for 60 seconds!

### 5. 🗺️ High-Precision Satellite GNSS & Interactive Google Maps
- Integrated with Google Maps Android SDK & Web Maps API (`AIzaSyByC7_EfClKMEGaeVQI5Q5b4BWfL2zVac4`).
- Multi-constellation GNSS satellite positioning with sub-5m accuracy.
- Continuous live GPS breadcrumb streaming to Supabase cloud database.

### 6. 💬 WhatsApp & SMS Live Coordinate Dispatch
- Instant 1-tap WhatsApp emergency alert dispatch with live Google Maps links and street address.
- 1-tap direct calling to Primary Guardian.

### 7. 🎙️ DITTU AI Voice Assistant
- Hands-free speech recognition and voice synthesis.
- Commands: `"HELP!"` (SOS trigger), `"Call Guardian"`, `"Where am I?"`, `"Share location to guardian"`.

---

## 📁 Repository Structure

```
.
├── README.md
├── .gitignore
├── safenex-android/                  # Native Android Application (Kotlin / Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/safenex/app/
│   │   │   ├── ble/                  # BLE GATT client & band connection
│   │   │   ├── data/                 # Data sources, Supabase GPS, Guardians, Schedules
│   │   │   ├── deterrent/            # 1200Hz Siren AudioTrack & Camera Strobe
│   │   │   ├── dttu/                 # DITTU voice recognition & synthesis
│   │   │   ├── service/              # Foreground Safety Service
│   │   │   └── ui/                   # Jetpack Compose UI Screens & ViewModels
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── build_apk.bat                 # Standalone APK compilation script
└── safenex-web/                      # Progressive Web Application (PWA)
    ├── index.html                    # Responsive Web App with Maps, Web Audio & Web Speech
    ├── server.ps1                    # Local web server script
    └── run_web.bat                   # Quick launcher
```

---

## 🚀 Getting Started

### 📱 Android Application
1. Connect an Android phone with USB Debugging enabled.
2. Compile and install using `build_apk.bat`:
   ```bash
   cd safenex-android
   build_apk.bat
   ```

### 🌐 Web Application
1. Run the local server:
   ```bash
   cd safenex-web
   run_web.bat
   ```
2. Open `http://localhost:8080/` in your browser.

---

## 🔒 Security Notice
- Default Safety Verification PIN: **`1234`**
