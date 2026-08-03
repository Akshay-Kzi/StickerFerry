# StickerFerry

Ferry Telegram sticker packs straight into WhatsApp.

[![Build](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml/badge.svg)](https://github.com/Akshay-Kzi/StickerFerry/actions/workflows/build.yml)

## Features

- Paste a Telegram sticker pack link → fetch, convert, and add to WhatsApp
- Supports static stickers (WebP), animated stickers (.tgs), and video stickers (.webm)
- Material 3 design with dynamic color (Material You)
- Works offline for previously fetched packs

## Tech Stack

Kotlin · Jetpack Compose · Hilt · Room · DataStore · Ktor · Kotlinx Serialization · Coil · WorkManager

## Quick Start

```bash
cp local.properties.example local.properties
# Edit local.properties with your bot token from @BotFather
```

Build with GitHub Actions or Android Studio, then install on device (API 26+).

## Disclaimer

This project is not affiliated with, endorsed by, or connected to WhatsApp LLC, Meta Platforms, Telegram Messenger Inc., or any of their affiliates. "WhatsApp" and "Telegram" are trademarks of their respective owners.

## Credits

StickerFerry is inspired by and builds upon patterns found in:
- [lolocomotive/stickers](https://github.com/lolocomotive/stickers) - For Material You design inspiration and WhatsApp integration patterns.

## License

MIT

