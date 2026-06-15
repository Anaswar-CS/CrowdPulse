# 📡 CrowdPulse — Offline Crowd Safety Network

> A phone-to-phone safety mesh network for large festivals and public gatherings — built entirely in Kotlin with Bluetooth LE. Zero internet. Zero towers. Zero compromise.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![BLE](https://img.shields.io/badge/Protocol-Bluetooth%20LE-0082FC?logo=bluetooth)](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 🧩 The Problem

At festivals like **Thrissur Pooram** (50,000+ attendees), cellular towers become completely overloaded. When someone gets lost or a crowd zone reaches dangerous density — there's no way to communicate, no way to alert, no way to coordinate.

**CrowdPulse solves this by turning every phone in the crowd into a safety sensor.**

---

## 🔥 Features

### 📍 Real-Time Location Sharing
- GPS coordinates broadcast phone-to-phone via BLE advertising
- No internet, no server — pure peer-to-peer relay
- Location hops across the mesh until it reaches your friends

### 🆘 One-Tap SOS
- SOS alert spreads to the entire crowd in seconds via custom flood routing
- TTL-based mesh propagation prevents infinite loops
- Delivered over GATT connections for reliability (bypasses 8-byte ad payload limit)

### 🚨 Auto Danger Zone Detection
- Monitors real-time crowd density using peer proximity data
- Fires an alert when density crosses **7 people/m²** — the internationally recognised stampede threshold
- Alerts all nearby devices automatically

### 💬 Offline Messaging
- Chat with friends via BLE GATT — like WhatsApp, but without WiFi
- Messages relay hop-by-hop through the mesh network

### 👥 Friend Finder
- Shows distance and compass bearing to each friend in your group
- Example: `↗ 42m • North-East`
- Calculated using the **Haversine formula** on live GPS data

### 🔋 Background Operation
- Runs as a foreground service — survives screen lock and battery optimisation
- Keeps the mesh alive all day with minimal battery impact

---

## ⚙️ How It Works

```
Phone A  ──BLE Adv──▶  Phone B  ──BLE Adv──▶  Phone C
   └──────GATT──────▶  Phone B  ──GATT──────▶  Phone C
```

1. **Discovery** — Every phone silently broadcasts a BLE advertisement packet containing its device ID and GPS coordinates
2. **Peer Registration** — Scanner picks up nearby peers and registers them in `PeerTable`
3. **Mesh Relay** — GATT connections carry full `MeshPacket` payloads (location, SOS, messages) between peers
4. **Flood Routing** — SOS and alerts use TTL-decremented flood routing to propagate across the entire crowd
5. **Density Monitoring** — `SafetyMonitor` counts peers within radius and triggers danger alerts at threshold

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Peer Discovery | BLE Advertising (`BleAdvertiser`) |
| Data Transport | BLE GATT (`GattServer`, `GattClient`, `GattManager`) |
| Mesh Routing | Custom flood routing with TTL (`MeshPacket`) |
| Distance & Bearing | Haversine Formula |
| Background Service | Android Foreground Service |
| Peer Registry | `PeerTable` (in-memory, 120s stale timeout) |
| Friend Management | `FriendManager` |
| Safety Monitoring | `SafetyMonitor` (thread-safe, main-thread Handler) |

---

## 📂 Project Structure

```
app/src/main/java/com/yourname/crowdpulse/
├── ble/
│   ├── BleAdvertiser.kt       # BLE advertisement broadcasting
│   ├── BleScanner.kt          # BLE peer discovery & scanning
│   ├── BleGattConfig.kt       # GATT service/characteristic UUIDs
│   ├── GattServer.kt          # GATT server (receives connections)
│   ├── GattClient.kt          # GATT client (initiates connections)
│   └── GattManager.kt         # Orchestrates GATT; broadcastToAll for SOS
├── mesh/
│   └── MeshPacket.kt          # Serialisable packet: location, SOS, message, TTL
├── safety/
│   └── SafetyMonitor.kt       # Crowd density monitoring & danger alerts
├── data/
│   ├── PeerTable.kt           # Live peer registry with stale-entry eviction
│   └── FriendManager.kt       # Friend list, message history, distance/bearing
├── ui/
│   ├── screens/               # Compose screens: Map, Chat, SOS, Friends
│   └── theme/                 # Material3 theme, dark mode
└── service/
    └── CrowdPulseService.kt   # Foreground service — keeps mesh alive
```

---

## 🧪 Tested On

- Two real Android phones in **Kerala, India**
- **Airplane mode ON** — confirmed zero internet dependency
- Successful: mutual peer discovery, GPS sharing, chat messaging, SOS propagation

---

## 📋 Permissions Required

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Two Android devices (API 26+) for real mesh testing
- BLE-capable hardware on both devices

### Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/crowdpulse.git
   cd crowdpulse
   ```

2. Open in Android Studio and sync Gradle.

3. Deploy to **two physical devices** (BLE does not work on emulators):
   ```bash
   ./gradlew installDebug
   ```

4. Grant Bluetooth + Location permissions on both devices.

5. Enable GPS, keep both phones within ~100m, and watch them discover each other.

---

---

## 🗺️ Roadmap

- [ ] Multi-hop message delivery (extend range beyond 1 hop)
- [ ] Organisers dashboard — aggregate crowd heatmap
- [ ] LoRa radio fallback for ultra-long-range relay
- [ ] Battery usage optimisation below 5% per hour
- [ ] iOS version (CoreBluetooth)

---

## 🤝 Contributing

Pull requests are welcome! For major changes, open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add your feature'`)
4. Push and open a Pull Request

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.


> *The denser the crowd, the stronger the network.*
> *CrowdPulse turns 50,000 phones at Thrissur Pooram into the most connected safety mesh in the world.*
