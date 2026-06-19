## Task 2 Report: Add dependencies (Compose-Bubble + Paho MQTT)

**Status:** Complete

### Changes Made

1. **`gradle/libs.versions.toml`** -- Added `pahoMqtt = "1.2.5"` version and two Paho library entries (`paho-mqtt-service`, `paho-mqtt-client`).

2. **`settings.gradle.kts`** -- Added JitPack repository `maven { url = uri("https://jitpack.io") }` to `dependencyResolutionManagement.repositories`.

3. **`app/build.gradle.kts`** -- Added three new dependencies:
   - `implementation(libs.paho.mqtt.service)` (Eclipse Paho Android Service)
   - `implementation(libs.paho.mqtt.client)` (Eclipse Paho MQTT Client)
   - `implementation("com.github.SmartToolFactory:Compose-Bubble:1.2.0")` (Compose Bubble via JitPack)

4. **`app/src/main/AndroidManifest.xml`** -- Added permissions (`INTERNET`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_DATA_SYNC`) and service declaration for `com.faster.tibot.service.TiBotForegroundService` with `foregroundServiceType="dataSync"`.

### Verification

- All files pass basic syntax inspection
- Version catalog entries follow existing naming conventions
- Service uses `com.faster.tibot` namespace matching Task 1 rename
