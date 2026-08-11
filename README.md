# Mneme

[![CI](https://github.com/LainsMain/Mneme/actions/workflows/ci.yml/badge.svg)](https://github.com/LainsMain/Mneme/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/LainsMain/Mneme)](https://github.com/LainsMain/Mneme/releases/latest)
[![Docker](https://img.shields.io/docker/v/egoisticfoil/mneme-server?label=server)](https://hub.docker.com/r/egoisticfoil/mneme-server)

Mneme is a private, native Android diary with flexible daily pages, lightweight
rich text, complete photo metadata, self-written monthly recaps, local search,
and end-to-end encrypted backup to a self-hosted server.

This repository currently contains the first vertical slice:

- A Kotlin/Compose Android app with dark-first Material 3 styling.
- A Journal / Month / Media / Map navigation shell inspired by content-first
  diary applications.
- A real month grid with writing indicators and support for per-day photo
  thumbnails; tapping any date opens it for editing.
- Fully editable date navigation and a non-binding late-night suggestion.
- An autosaving Room-backed rich-text document with bold, italic, underline,
  heading, and strike-through formatting.
- Persistence models for original photo files and full EXIF/GPS metadata.
- A keyless MapLibre map, OpenFreeMap tiles, and self-hosted Photon autocomplete.
- Android-side AES-GCM vault encryption with manual and scheduled backups.
- A Go service with hashed per-device tokens and opaque object storage.
- Docker Compose deployment with optional remotely managed Cloudflare Tunnel.
- Automatic GitHub release checks and app/server version compatibility notices.

## Android

Requirements: JDK 17 and an Android SDK containing API 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk-17
./gradlew :android:app:assembleDebug
```

The debug APK is written under `android/app/build/outputs/apk/debug/`.

### Maps and place search

The map uses MapLibre with keyless OpenFreeMap styles. Location autocomplete is
proxied through the authenticated Mneme server to its self-hosted Photon index,
so diary clients do not contact a commercial places API. Connect the server in
Settings, then use **Add a place** on any entry.

## Server

```bash
cd server
go test ./...
go run ./cmd/mneme token create --name "Development phone"
go run ./cmd/mneme serve
```

The health endpoint is `http://localhost:8080/v1/health`. See
[`deploy/README.md`](deploy/README.md) for Docker and Cloudflare setup.

## Releases and updates

Stable APKs are published on the [GitHub Releases page](https://github.com/LainsMain/Mneme/releases).
Mneme checks that feed when it opens and offers a Download/Later dialog for a
newer version; Settings also contains a manual check. The connected server
reports its embedded release version, allowing the app to warn when the Docker
deployment is older than the latest matching release.

A semantic tag automatically builds the signed APK and publishes
`egoisticfoil/mneme-server:<version>` plus `latest`. See
[`RELEASING.md`](RELEASING.md) for the required encrypted repository secrets.

## Important security boundary

The server is intentionally an opaque blob store: manifests and original photos
are AES-256-GCM encrypted on Android before upload. A tested restore flow and
user-held recovery-key export are still required before treating backups as the
only copy of important diary data.
