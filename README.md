# StickerFerry ⛴️

**StickerFerry** is a modern Android application designed to bridge the gap between Telegram and WhatsApp sticker ecosystems. It allows you to effortlessly "ferry" your favorite Telegram sticker packs into WhatsApp with a single link.

[![Get it from GitHub Releases](https://img.shields.io/badge/Get_it_from-GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Akshay-Kzi/StickerFerry/releases/latest)

[![Build Status](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml/badge.svg)](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## ✨ Features

- **Link-to-Pack Integration**: Just paste a `t.me/addstickers/...` link (or a bare pack name), and the app handles the rest.
- **Smart Splitting**: Telegram packs often exceed WhatsApp's 30-sticker limit. StickerFerry splits large packs into balanced, numbered parts (62 stickers become 21/21/20 rather than 30/30/2), so no part is rejected for falling below WhatsApp's 3-sticker minimum.
- **All Sticker Formats**: Static WebP, animated `.tgs`, and video `.webm` stickers are all converted to WhatsApp-compatible files kept under its 500 KB size limit.
- **Mixed Packs**: WhatsApp requires a pack to be entirely static or entirely animated, so mixed Telegram packs are automatically split into separate static and animated parts.
- **Material 3 & Dynamic Color**: Fully adopts the Material You design system with support for light/dark modes and dynamic system colors.
- **Deep Link Support**: Automatically opens Telegram sticker links shared from other apps.
- **Offline Library**: Previously converted packs are stored locally, allowing you to re-add them to WhatsApp without re-downloading.
- **Background Processing**: Efficiently handles sticker downloads and conversions without blocking the UI.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Dependency Injection**: Hilt
- **Networking**: Ktor Client with Kotlinx Serialization
- **Local Storage**: 
  - **Room Database**: For sticker pack metadata and management.
  - **SharedPreferences**: For the optional bot token override.
- **Image Loading**: Coil (with custom WebP support)
- **Concurrency**: Kotlin Coroutines & Flow
- **Navigation**: Compose Navigation

## 📥 Install

You can download StickerFerry as an APK through GitHub Releases

1. Open the [latest release](https://github.com/Akshay-Kzi/StickerFerry/releases/latest) and download `app-release.apk` (about 14 MB).
2. Open the downloaded file on your phone. If Android prompts you, allow installs from your browser or file manager.
3. Launch StickerFerry, paste a `t.me/addstickers/...` link (or share one to the app from Telegram), then tap **Convert to WhatsApp**.
4. For packs with more than 30 stickers, add each numbered part from the result screen — StickerFerry points you to the next one.

Requires Android 8.0 (API 26) or newer and WhatsApp or WhatsApp Business installed.

## 🚀 Build from Source

### Prerequisites
- Android Studio Iguana (2023.2.1) or newer.
- JDK 17.
- Android SDK with API 35.
- A Telegram Bot Token from [@BotFather](https://t.me/botfather), used for the Bot API calls.

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/Akshay-Kzi/StickerFerry.git
   ```
2. Set up your environment variables:
   - Copy `local.properties.example` to `local.properties`.
   - Add your `TELEGRAM_BOT_TOKEN` to `local.properties`.
   ```properties
   TELEGRAM_BOT_TOKEN=your_token_here
   ```
   > `local.properties` is gitignored for a reason: the token is compiled into the APK. Never commit it or share a build made with it.
3. Sync the project with Gradle and run it on your device/emulator.
4. To build an installable APK from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.

## 📖 How it Works

1. **Discovery**: The user inputs a Telegram sticker set shortname or link.
2. **Fetching**: StickerFerry uses the Telegram Bot API to fetch pack metadata and file identifiers.
3. **Processing**: 
   - Stickers are downloaded to the app's internal cache and converted to WhatsApp's format — static stickers are resized to 512×512 and re-encoded under 100 KB, while animated `.tgs` and video `.webm` stickers are re-encoded into animated WebP under WhatsApp's 500 KB limit.
   - Stickers are grouped by type (static or animated) and split into balanced parts of at most 30 stickers to comply with WhatsApp's limits.
   - Metadata (name, publisher, tray icon) is prepared for WhatsApp.
4. **Handoff**: The app triggers an intent to WhatsApp's `StickerPackPublisher`, which uses a `ContentProvider` to safely read the sticker data from StickerFerry's storage.

## ❓ FAQ

**Why do I see several packs for one Telegram pack?** WhatsApp caps a pack at 30 stickers, so anything larger is split into numbered parts. Add them one by one from the result screen. You only need to do this once; parts are also kept in the offline library.

**Does StickerFerry need Telegram installed?** No. It talks to the Telegram Bot API directly. WhatsApp (or WhatsApp Business) does need to be installed, since that's where the packs are handed off.

## 🤝 Credits & Inspiration

- **WhatsApp Sticker Library**: This app uses the official WhatsApp sticker integration protocols.
- **lolocomotive/stickers**: Inspired some integration workflows.

## ⚖️ Disclaimer

StickerFerry is an independent project and is not affiliated with, endorsed by, or connected to **WhatsApp LLC**, **Meta Platforms**, **Telegram Messenger Inc.**, or any of their affiliates. "WhatsApp" and "Telegram" are trademarks of their respective owners.

Use this app responsibly and respect the copyright of sticker creators.

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

--

>[!NOTE]
>This project was developed with help of LLM coding tools. Feel free to refrain from using if you do not like projects built using AI.  
