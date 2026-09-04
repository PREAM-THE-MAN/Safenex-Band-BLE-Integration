# SAFENEX Android Application - Phase 2 (BLE Central & Emergency Orchestration)

SAFENEX is an Android-only personal emergency safety application acting as a **BLE Central / Client** connecting directly to the **SAFENEX-BAND** ESP32 wearable hardware device.

---

## 📡 BLE Device & GATT Specifications

| Parameter | Specification |
| :--- | :--- |
| **Advertised Device Name** | `SAFENEX-BAND` |
| **Primary Service UUID** | `7b7a1000-8f2a-4c5e-9b11-123456789001` |
| **SOS Characteristic UUID** | `7b7a1001-8f2a-4c5e-9b11-123456789001` (Notify / Read) |
| **STATUS Characteristic UUID** | `7b7a1002-8f2a-4c5e-9b11-123456789001` (Notify / Read) |
| **CCCD Descriptor UUID** | `00002902-0000-1000-8000-00805f9b34fb` |

---

## 🚀 Key Architecture & Components

```
+-----------------------------------------------------------------------------------+
|                                 SAFENEX UI LAYER                                  |
|  +---------------------+   +-----------------------+   +-----------------------+  |
|  |     HomeScreen      |   |    EmergencyScreen    |   |  VerifySafetyScreen   |  |
|  | - Status / Scan btn |   | - !! EMERGENCY MODE !!|   | - Secure PIN Keypad   |  |
|  | - DITTU / Standby   |   | - Trigger (Btn/Shake) |   | - Hash Verification   |  |
|  +----------+----------+   +-----------+-----------+   +-----------+-----------+  |
+-------------|--------------------------|---------------------------|--------------+
              |                          |                           |
              +--------------------------+---------------------------+
                                         |
                                         v
                         +-------------------------------+
                         |         MainViewModel         |
                         +---------------+---------------+
                                         |
              +--------------------------+--------------------------+
              |                                                     |
              v                                                     v
+-------------------------------+                     +-------------------------------+
|       EmergencyManager        |                     |          BleManager           |
| - State Machine:              |                     | - GATT Client & Lifecycle     |
|   SAFE -> EMERGENCY_ACTIVE    |                     | - Service & Characteristic    |
|   -> VERIFYING                |                     |   Discovery                   |
|   -> SAFETY_VERIFIED -> SAFE  |                     | - CCCD Notification (0x2902)  |
| - Retains EMERGENCY across    |                     | - Reconnection & Resilience   |
|   BLE Disconnects             |                     +---------------+---------------+
+---------------+---------------+                                     |
                ^                                                     v
                |                                     +-------------------------------+
                |                                     |          BleScanner           |
                |                                     | - Targeted Scan (SAFENEX-BAND)|
                |                                     | - Scan Timeout (12s)          |
                |                                     +---------------+---------------+
                |                                                     |
                |                                                     v
                |                                     +-------------------------------+
                +-------------------------------------+      SafenexBandProtocol      |
                         SOS:BUTTON / SOS:SHAKE       | - UUIDs                       |
                                                      | - Packet Parsing              |
                                                      +-------------------------------+
```

---

## 🛡️ Emergency State Machine

1. **SAFE (Normal Standby)**:
   - Band status displays `DISCONNECTED` or `CONNECTED`.
   - Continuous background listening for SOS notifications.
2. **EMERGENCY_ACTIVE**:
   - Triggered when `SOS:BUTTON` or `SOS:SHAKE` notification is received.
   - Immediately replaces the Home screen with the high-contrast `EmergencyScreen`.
   - Displays trigger source (`Button` or `Vigorous Shake`).
   - If the band disconnects while active, the emergency session **persists** and displays:
     > *"Band connection lost. Emergency session remains active."*
3. **VERIFYING**:
   - Triggered when user taps `VERIFY SAFETY`.
   - Opens the secure local PIN input screen.
4. **SAFETY_VERIFIED**:
   - Upon entering the valid PIN (Default: `1234`), displays confirmation and cleanly disarms to `SAFE`.

---

## 🔒 Security Architecture

- PIN is **never stored in plain text**.
- Uses **Salted SHA-256 with 16-byte random cryptographic salt** and constant-time equality check (`MessageDigest.isEqual`) to prevent timing side-channel attacks.

---

## 📱 How to Run the App

1. Open Android Studio.
2. Select **Open** and choose this directory: `C:\Users\pream\.gemini\antigravity\scratch\safenex-android`.
3. Let Gradle sync and build.
4. Run on an Android device (Android 8.0+ / API 26+) or emulator with Bluetooth enabled.
5. Tap **SCAN FOR BAND** to connect to the physical `SAFENEX-BAND` ESP32 wearable.
6. Test SOS triggers from the physical ESP32 or using the embedded Test Simulation Controls.
