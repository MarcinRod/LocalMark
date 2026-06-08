# LocalMark

A reference Android application demonstrating how to integrate Google Maps, Firebase, and related platform services within a single Jetpack Compose codebase, following modern Android architecture conventions.

---

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Architecture](#architecture)
4. [Tech Stack](#tech-stack)
5. [Project Setup](#project-setup)
6. [Key API Documentation](#key-api-documentation)
7. [Module Structure](#module-structure)
8. [Contributing](#contributing)

---

## Overview

LocalMark is an Android teaching project that demonstrates how to use  several Google and Firebase services inside a single, production-like Compose application. It is intentionally scoped to be easy to read and follow while still covering real-world patterns such as unidirectional data flow, offline-first data persistence, camera and file-provider flows, and OAuth sign-in.

---

## Features

| Feature | Details |
|---|---|
| Interactive map | Pan, zoom, and drop markers via **Maps SDK for Android** (Compose wrapper) |
| Place search | Autocomplete-powered search bar powered by **Google Places SDK** |
| Live location | One-shot and continuous device location via **Fused Location Provider** |
| Reverse geocoding | Tap-to-address lookup via the **Google Maps Geocoding REST API** |
| Photo capture | In-app camera with EXIF correction and resizing before upload |
| Cloud sync | Markers stored in **Cloud Firestore**; photos in **Firebase Cloud Storage** |
| Authentication | Email/password and Google One Tap sign-in via **Firebase Authentication** |

---

## Architecture

```
app/
├── data/          # Repositories, remote data sources, DTOs
├── di/            #  manual DI modules
├── location/      # Fused Location Provider wrappers
├── ui/
│   ├── components/  # Reusable Compose components (buttons, cards, …)
│   ├── helpers/     # UiText, extension functions
│   ├── navigation/  # NavGraph + route definitions
│   ├── screens/
│   │   ├── map/     # Map screen + ViewModel
│   │   └── signin/  # Sign-in screen + ViewModel
│   └── theme/       # Color, typography, shapes, spacing tokens
└── util/          # General-purpose utilities
```

The app follows a **unidirectional-data-flow** pattern: each screen has a ViewModel initialized via a viewModelFactory approach.

---


## Project Setup

### Prerequisites

* A Google Cloud project with the following APIs enabled:
  * Maps SDK for Android
  * Geocoding API
  * Places API (New)
* A Firebase project with Authentication, Firestore, and Storage enabled

### Steps

1. **Clone the repo**

2. **Add your `google-services.json`**

   Download it from the Firebase Console and place it at `app/google-services.json`.

3. **Configure API keys**

   Copy `local.defaults.properties` to `local.properties` (already git-ignored) and fill in your keys:

   ```properties
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_KEY
   PLACES_API_KEY=YOUR_PLACES_KEY
   GEOCODING_API_KEY=YOUR_GEOCODING_KEY
   ``` 

   The Secrets Gradle Plugin reads these at build time and injects them into `BuildConfig` / the manifest.

4. **Build and run**


---

## Key API Documentation

Each integration is documented in detail in the [`docs/`](docs/) folder:

| # | Topic | File |
|---|---|---|
| 1 | Google Maps (Maps Compose) | [docs/01_google_maps.md](docs/01_google_maps.md) |
| 2 | Geocoding REST API | [docs/02_geocoding.md](docs/02_geocoding.md) |
| 3 | Google Places SDK | [docs/03_places.md](docs/03_places.md) |
| 4 | Fused Location Provider | [docs/04_location_provider.md](docs/04_location_provider.md) |
| 5 | Firebase Authentication & OAuth | [docs/05_firebase_auth.md](docs/05_firebase_auth.md) |
| 6 | Cloud Firestore | [docs/06_firebase_firestore.md](docs/06_firebase_firestore.md) |
| 7 | Firebase Cloud Storage | [docs/07_firebase_cloud_storage.md](docs/07_firebase_cloud_storage.md) |
| 8 | Camera & Image Processing | [docs/08_camera_image_processing.md](docs/08_camera_image_processing.md) |

---


### Built as a teaching reference for modern Android development.



