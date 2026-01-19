# Vindex

A privacy-focused photo gallery app for Android with local AI features (100% offline).

## Features

- 📷 Photo gallery with customizable grid
- 🔍 Search by filename (semantic search coming soon)
- 👤 Face recognition and person management
- 🏷️ AI-powered tagging (coming soon)
- 📝 OCR text detection (coming soon)
- 🌍 Offline reverse geocoding
- 🌙 Light/Dark theme with Material You support
- 🌐 English and French localization

## Privacy First

This app is designed with privacy as a core principle:

- **No internet permission** — The app cannot connect to the internet
- **No analytics** — No tracking, no telemetry
- **No cloud sync** — All data stays on your device
- **Local AI only** — AI models run entirely on-device

## Requirements

- Android 8.0 (API 26) or higher
- Storage permission to access your photos

## Building

```bash
git clone https://github.com/yourusername/vindex.git
cd vindex
./gradlew assembleDebug
```

## Data Files

### Geographic Data (Optional)

For offline reverse geocoding (converting GPS coordinates to city names), download the GeoNames cities database:

1. Download: https://download.geonames.org/export/dump/cities15000.zip
2. Extract `cities15000.txt` from the ZIP
3. Place it in `app/src/main/assets/`

The app will work without this file, but location names won't be resolved.

### AI Models (Future)

AI models are not included in the APK to keep the app size small. They will be imported manually by the user when AI features are implemented.

## Licenses and Attributions

This project is licensed under **GPL-3.0**. See [LICENSE](LICENSE) for details.

Third-party data and libraries are listed in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

### Required Attributions

- **GeoNames**: Geographic data provided by [GeoNames](https://www.geonames.org/) under CC BY 4.0 license.

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting a PR.

## Roadmap

- [x] Basic gallery with grid view
- [x] Photo viewer with zoom and swipe
- [x] EXIF metadata extraction
- [x] Face detection and person management
- [x] Offline reverse geocoding
- [ ] Semantic search with AI captioning
- [ ] AI-powered tagging (RAM/RAM++)
- [ ] OCR text detection
- [ ] Auto-generated albums (events, locations)
- [ ] Duplicate detection

## Contact

For bugs and feature requests, please open an issue on GitHub.