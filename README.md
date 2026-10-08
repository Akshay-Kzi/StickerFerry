# StickerFerry ⛴️

**StickerFerry** is a modern Android application designed to bridge the gap between Telegram and WhatsApp sticker ecosystems. It allows you to effortlessly "ferry" your favorite Telegram sticker packs into WhatsApp with a single link.

[![Build Status](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml/badge.svg)](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2023.10.01-green.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## ✨ Features

- **Link-to-Pack Integration**: Just paste a `t.me/addstickers/...` link, and the app handles the rest.
- **Smart Splitting**: Telegram packs often exceed WhatsApp's 30-sticker limit. StickerFerry automatically splits large packs into manageable parts while maintaining consistency.
- **Multi-Format Support**: Handles static (WebP) stickers. Due to WhatsApp's strict limitations, the app currently only supports sticker packs with fewer than 30 stickers. Support for animated (.tgs) and video (.webm) stickers, as well as the ability to split larger packs into multiple parts, is currently in development and yet to be fully implemented.
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
  - **DataStore**: For user preferences.
- **Image Loading**: Coil (with custom WebP support)
- **Concurrency**: Kotlin Coroutines & Flow
- **Background Tasks**: WorkManager
- **Navigation**: Compose Navigation

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana (2023.2.1) or newer.
- Android API level 26+ (Android 8.0).
- A Telegram Bot Token from [@BotFather](https://t.me/botfather).

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
3. Sync the project with Gradle and run it on your device/emulator.

## 📖 How it Works

1. **Discovery**: The user inputs a Telegram sticker set shortname or link.
2. **Fetching**: StickerFerry uses the Telegram Bot API to fetch pack metadata and file identifiers.
3. **Processing**: 
   - Stickers are downloaded to the app's internal cache.
   - Large packs are split into parts (Part 1, Part 2, etc.) to comply with WhatsApp's 30-sticker limit.
   - Metadata (name, publisher) is prepared for WhatsApp.
4. **Handoff**: The app triggers an intent to WhatsApp's `StickerPackPublisher`, which uses a `ContentProvider` to safely read the sticker data from StickerFerry's storage.

## 🤝 Credits & Inspiration

- **WhatsApp Sticker Library**: This app uses the official WhatsApp sticker integration protocols.
- **lolocomotive/stickers**: Inspired some integration workflows.

## ⚖️ Disclaimer

StickerFerry is an independent project and is not affiliated with, endorsed by, or connected to **WhatsApp LLC**, **Meta Platforms**, **Telegram Messenger Inc.**, or any of their affiliates. "WhatsApp" and "Telegram" are trademarks of their respective owners.

Use this app responsibly and respect the copyright of sticker creators.

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.
