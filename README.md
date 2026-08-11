# Mneme

[![CI](https://github.com/LainsMain/Mneme/actions/workflows/ci.yml/badge.svg)](https://github.com/LainsMain/Mneme/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/LainsMain/Mneme)](https://github.com/LainsMain/Mneme/releases/latest)
[![Docker](https://img.shields.io/docker/v/egoisticfoil/mneme-server?label=server)](https://hub.docker.com/r/egoisticfoil/mneme-server)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

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
- A keyless MapLibre map, OpenFreeMap tiles, and device-native location search.
- Android-side AES-256-GCM vault encryption, after-edit and periodic backups,
  and recovery-code restore onto a new phone.
- A Go service with hashed per-device tokens and opaque object storage.
- Docker Compose deployment with optional remotely managed Cloudflare Tunnel.
- Automatic GitHub release checks, verified in-app APK downloads, and app/server
  version compatibility notices.

## Android

Requirements: JDK 17 and an Android SDK containing API 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk-17
./gradlew :android:app:assembleDebug
```

The debug APK is written under `android/app/build/outputs/apk/debug/`.

### Maps and place search

The map uses MapLibre with keyless OpenFreeMap styles. Search and reverse
geocoding use Android's built-in geocoder, while manual map pinning remains
available when a device geocoder cannot return a result. Photo GPS coordinates
are reverse-geocoded automatically. The backup server is not involved in maps.

## Server

The production [Compose file](compose.yaml) is kept at the repository root and
is also attached to every GitHub release. On a Docker host:

```bash
curl -LO https://github.com/LainsMain/Mneme/releases/latest/download/compose.yaml
curl -LO https://github.com/LainsMain/Mneme/releases/latest/download/mneme.env.example
cp mneme.env.example .env
docker compose up -d
docker compose exec mneme-server /mneme token create --name "My phone"
```

Add `--profile tunnel` to `docker compose up -d` after putting a remotely
managed Cloudflare tunnel token in `.env`.

For development directly from the source tree:

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
newer version; Settings also contains a manual check. APK downloads use
Android's Download Manager for background progress and are checked against the
SHA-256 digest published by GitHub before Android's installer is opened. The connected server
reports its embedded release version, allowing the app to warn when the Docker
deployment is older than the latest matching release.

A semantic tag automatically builds the signed APK and publishes
`egoisticfoil/mneme-server:<version>` plus `latest`. See
[`RELEASING.md`](RELEASING.md) for the required encrypted repository secrets.

## Backup and recovery

The server is intentionally an opaque blob store: manifests and original photos
are AES-256-GCM encrypted on Android before upload. Mneme schedules a backup
shortly after diary changes when online and keeps a roughly six-hour periodic
fallback. Android may defer background work during Doze or while offline.

Settings shows the last successful backup and a portable recovery code. Store
that code outside the phone: a fresh Mneme installation can connect to the same
server, choose **Restore on this device**, and decrypt the latest matching
backup with it. Neither the server token nor the server administrator can
decrypt a backup without the recovery code. Backups created before 0.1.5 used a
device-bound Android Keystore key, so upgrade users must make one fresh backup
and save the new code before relying on disaster recovery.

## Security

Do not post suspected vulnerabilities or exposed credentials in a public issue.
See [SECURITY.md](SECURITY.md) for private reporting and credential-handling
guidance. The repository uses secret scanning, push protection, and an
independent full-history scan in CI.

## License

Mneme is available under the [MIT License](LICENSE).
