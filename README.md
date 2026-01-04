# Vindex

**Vindex** is an Android photo gallery application designed with AI-readiness in mind. This repository contains the **base application** — a fully functional gallery with a clean architecture prepared to integrate local AI features in future iterations.

## 🎯 Project Vision

Vindex aims to be a privacy-focused photo gallery with intelligent search capabilities powered entirely by **on-device AI** (no cloud, no internet required). The project is developed in three phases:

1. **Base App** (this repository) — Core gallery functionality with prepared architecture
2. **POC 1: Captioning** — Semantic search via AI-generated descriptions and embeddings
3. **POC 2: Multi-AI Tags** — Face recognition, OCR, and open tagging with RAM/RAM++

The final application will merge both approaches for comprehensive intelligent photo management.

## ✨ Features (Base App)

### Current Features
- 📁 Folder-based photo browsing using Storage Access Framework (SAF)
- 🖼️ Grid gallery view with customizable columns (2-5)
- 📅 Photos sorted by date with visual separators
- 🔍 Basic search by filename and path
- 📂 Album view based on folder structure
- 👤 People section (UI ready, awaiting AI integration)
- 🌓 Material Design 3 with dynamic theming (Material You on Android 12+)
- 🌍 Internationalization: English and French
- ⚙️ Settings screen with prepared AI configuration sections

### Photo Viewer
- Pinch-to-zoom and pan navigation
- Swipe between photos
- Information panel showing:
  - EXIF metadata (date, location, camera)
  - Placeholder sections for AI-generated content (description, tags, detected faces, OCR text)

### Prepared for Future AI Features
- Database schema includes all fields for AI data storage
- Interface definitions for AI engines (captioning, embedding, tagging, face detection, OCR)
- WorkManager setup for background processing
- Progress notification system for long-running tasks

## 🔒 Privacy First

Vindex is designed as a **100% offline application**:
- ❌ No INTERNET permission in the manifest
- ❌ No analytics, crash reporting, or cloud services
- ✅ All AI models will be imported manually by the user
- ✅ Your photos never leave your device

## 📱 Requirements

- **Android 8.0** (API 26) or higher
- Storage permission to access photos

## 🏗️ Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| Architecture | MVVM |
| Database | Room |
| UI | Views + ViewBinding |
| Navigation | Jetpack Navigation |
| Image Loading | Glide |
| Background Work | WorkManager |
| Design | Material Design 3 |

## 📂 Project Structure

```
com.cevague.vindex/
├── data/
│   ├── database/
│   │   ├── dao/          # Data Access Objects
│   │   └── entity/       # Room entities
│   ├── repository/       # Data repositories
│   └── model/            # POJOs, DTOs
├── ui/
│   ├── main/             # Main activity & navigation
│   ├── gallery/          # Photo grid
│   ├── viewer/           # Photo viewer
│   ├── albums/           # Albums list & detail
│   ├── search/           # Search interface
│   ├── people/           # Face gallery (trombinoscope)
│   ├── settings/         # App settings
│   └── common/           # Shared UI components
├── service/              # Background services
├── ai/                   # AI engine interfaces (prepared)
└── util/                 # Utility classes
```

## 🗄️ Database Schema

The Room database includes tables prepared for all planned features:

- **photos** — Indexed photos with EXIF data and AI fields
- **persons** — Identified people with centroid embeddings
- **faces** — Detected faces with bounding boxes and embeddings
- **albums** — Folder-based, manual, and auto-generated albums
- **album_photos** — Many-to-many relationship for albums
- **ai_models** — Configuration for imported AI models
- **settings** — App preferences
- **photo_hashes** — For duplicate detection
- **cities** — GeoNames data for reverse geocoding
- **analysis_log** — Processing history for debugging

## 🚀 Getting Started

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/CeVague/Vindex_Base.git
   ```

2. Open the project in Android Studio (Ladybug or newer recommended)

3. Sync Gradle and build:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on your device or emulator

### First Launch

1. Grant storage permission when prompted
2. Select a folder containing your photos
3. The app will scan and index your photos
4. Browse your gallery!

## 🛣️ Roadmap

### Base App (Current)
- [x] Project setup and architecture
- [ ] Room database implementation
- [ ] Main navigation with 4 tabs
- [ ] Gallery grid with date separators
- [ ] Photo viewer with zoom/pan
- [ ] Album management
- [ ] Search by filename
- [ ] Settings screen
- [ ] Theming and i18n

### Future: POC 1 - Captioning
- [ ] ONNX Runtime integration
- [ ] ViT-GPT2 / BLIP captioning models
- [ ] MiniLM / E5 embedding models
- [ ] Semantic search engine
- [ ] ML Kit translation for queries

### Future: POC 2 - Multi-AI Tags
- [ ] MediaPipe BlazeFace detection
- [ ] MobileFaceNet embeddings
- [ ] Face clustering and naming UI
- [ ] ML Kit / PaddleOCR text recognition
- [ ] RAM/RAM++ open tagging
- [ ] Multi-criteria search
- [ ] Automatic album generation

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

As this project uses the GPL-3.0 license, any modifications must also be released under the same license.

## 📄 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

This project will integrate several open-source AI models and libraries:
- [ONNX Runtime](https://onnxruntime.ai/) for model inference
- [MediaPipe](https://developers.google.com/mediapipe) for face detection
- [ML Kit](https://developers.google.com/ml-kit) for OCR and translation
- [Glide](https://github.com/bumptech/glide) for image loading
- [PhotoView](https://github.com/Baseflow/PhotoView) for zoom functionality

---

*Vindex — Your photos, your device, your privacy.*