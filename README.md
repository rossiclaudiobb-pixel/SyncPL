# SyncPL – Sync macOS Music Playlists to Android over Wi-Fi

[![Official Website](https://img.shields.io/badge/Website-syncpl.org-blue)](https://syncpl.org)
[![Italian Version](https://img.shields.io/badge/Language-Italiano-red)](https://syncpl.org/it.html)
[![License](https://img.shields.io/badge/License-Pro%20%2F%20Free%20Trial-green)](https://syncpl.org/#buy)

**SyncPL** is a lightweight desktop and mobile utility designed to wirelessly sync playlists and audio tracks from the macOS Music app to Android devices over local Wi-Fi.

No cables, no cloud servers, and no recurring subscriptions.

---

## 🔗 Quick Links

* **Official Website:** [https://syncpl.org](https://syncpl.org)
* **Italian Version:** [https://syncpl.org/it.html](https://syncpl.org/it.html)
* **Buy Pro License:** [Get Unlock Code on Gumroad](https://claudibreeze5.gumroad.com/l/syncpl)
* **Support Email:** [support@syncpl.org](mailto:support@syncpl.org)

---

## ⚡ Key Features

- **Local Wi-Fi Sync:** Bypass USB cable headaches and MTP protocol failures on macOS. Connect your Mac and Android phone to the same local network to sync in one click.
- **100% Private & Offline:** Your audio files never pass through external cloud servers. All transfers occur directly between your Mac and Android device.
- **Preserves Playlist Structure:** Retain track ordering, metadata, and playlist hierarchies created in the macOS Music app directly on your Android music player.
- **Apple Silicon Native:** Fully optimized for M1, M2, M3, and M4 Macs running macOS 10.15+ and Android 8.0+.

---

## 💳 Download & Pricing

* **Free Trial:** Free download with no time limit. Perfect for testing hardware compatibility (limited to 2 playlists and up to 8 tracks per playlist).
* **Pro License (€19 One-Time):** Unlocks unlimited playlists, unlimited tracks, fast differential sync, and lifetime updates.

---

## 📬 Contact & Support

For questions, feedback, or technical inquiries:
- **Official Website:** [syncpl.org](https://syncpl.org)
- **Support Email:** support@syncpl.org




# SyncPL 🎵

**SyncPL** is a fast, lightweight, and modern Android application designed to wirelessly synchronize music playlists exported from your Mac (Apple Music / Music app / iTunes) directly to your Android device over local Wi-Fi.

---

## ✨ Features

- ⚡ **Zero-Config Connection (mDNS / Bonjour)**: Automatically connects to your Mac via Multicast DNS (`http://syncpl.local:5001`). No need to manually look up or enter IP addresses.
- 🔄 **Smart Delta Synchronization**:
  - Downloads only new or missing audio files.
  - Automatically deletes tracks removed from playlists on Mac.
  - Cleans up orphan playlist folders if a playlist is deleted.
- 📑 **Automatic `.m3u` Generation**: Automatically generates `.m3u` playlist files in each local playlist folder for seamless compatibility with Android music players (Poweramp, Musicolet, VLC, etc.).
- 🔀 **Duplicate Track Handling**: Deterministically handles tracks with duplicate filenames located in different album folders without file collisions or endless re-download loops.
- 🌐 **Unicode & macOS NFD/NFC Normalization**: Perfectly handles special characters, accents, and macOS file system differences.
- 🔔 **Background Foreground Service**: Syncs in the background with real-time notifications, progress tracking, and detailed post-sync reports (downloaded files, removed files, errors).
- 🎨 **Jetpack Compose & Material 3**: Beautiful, modern English UI with responsive controls.

---

## 🛠️ How It Works
1. **Launch the Mac App:** Open the SyncPL companion app on macOS. It automatically detects your local Apple Music library.
2. **Connect Android:** Open the SyncPL app on Android connected to the same Wi-Fi network. The Mac server will be discovered instantly.
3. **Sync Wireless:** Select the playlists you want to transfer and start syncing effortlessly without cables.


```
┌──────────────────────────┐               Wi-Fi (mDNS / HTTP)               ┌──────────────────────────┐
│         Mac Server       │  -------------------------------------------->  │      SyncPL Android      │
│  (playlist_esportate.txt)│     http://syncpl.local:5001/playlist          │   App & Background Service│
└──────────────────────────┘                                                 └─────────────┬────────────┘
                                                                                           │
                                                                                           ▼
                                                                             ┌──────────────────────────┐
                                                                             │      Phone Storage       │
                                                                             │  Music/                  │
                                                                             │    ├── PlaylistA/        │
                                                                             │    │    ├── song1.mp3    │
                                                                             │    │    └── PlaylistA.m3u│
                                                                             └──────────────────────────┘
```

---

## 🚀 Getting Started

### 1. Mac Server Setup

Run a lightweight HTTP server on your Mac on port `5001` that serves:
1. `GET /playlist`: Returns the contents of your exported playlist file (`playlist_esportate.txt`).
2. `GET /file?path=<mac_path>`: Streams the requested audio file.

#### Example Python Server (`server.py`)

```python
from flask import Flask, request, send_file, Response
import os

app = Flask(__name__)
PLAYLIST_FILE = os.path.expanduser("~/Music/playlist_esportate.txt")

@app.route('/playlist', methods=['GET'])
def get_playlist():
    if os.path.exists(PLAYLIST_FILE):
        with open(PLAYLIST_FILE, 'r', encoding='utf-8', errors='ignore') as f:
            return Response(f.read(), mimetype='text/plain; charset=utf-8')
    return "Playlist file not found", 404

@app.route('/file', methods=['GET'])
def get_file():
    file_path = request.args.get('path')
    if file_path and os.path.exists(file_path):
        return send_file(file_path)
    return "File not found", 404

if __name__ == '__main__':
    # Listen on port 5001
    app.run(host='0.0.0.0', port=5001)
```

> **Note**: Make sure your Mac hostname is reachable at `syncpl.local` or Bonjour/mDNS is active on your local Wi-Fi network.

### 2. Android App Setup

1. Open **SyncPL** on your Android device.
2. Go to **Settings** and select your local music destination folder (e.g., `Music`).
3. (Optional) Enter a manual IP address if your local Wi-Fi router blocks mDNS broadcasts. Otherwise, leave it blank to automatically use `syncpl.local`.
4. Tap **Sync** on the main screen to start synchronizing!

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Networking**: OkHttp 4
- **Background Work**: Android Foreground Service & Notifications
- **Storage**: Jetpack DataStore Preferences & Storage Access Framework (SAF DocumentFile)
- **Concurrency**: Kotlin Coroutines & StateFlow

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
