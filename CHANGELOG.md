# Changelog

## 0.1.5

- Add portable recovery codes and a safe new-phone restore flow for encrypted diary entries, original photos, metadata, and monthly recaps.
- Back up about 30 seconds after diary changes when online, with the existing six-hour periodic job retained as a fallback.
- Show the last successful backup and require an explicit recovery-code save reminder.
- Download updates through Android with notification progress, verify GitHub's SHA-256 digest, and open the system installer when ready.
- Check for releases on every fresh app launch and whenever a long-running app returns to the foreground after the cache expires.
- Open the Android biometric prompt automatically whenever a protected diary locks again.

## 0.1.4

- Move place search and reverse geocoding to Android's built-in geocoder.
- Add a full-screen map pin picker for locations the device cannot name.
- Automatically name primary-photo GPS locations when reverse geocoding is available.
- Reduce the self-hosted server to encrypted backup storage by removing its geocoder service.

## 0.1.3

- Keep the active journal cursor visible above the keyboard and formatting controls while typing.
- Reserve layout space for the formatting bar and retain free swipe-scrolling through long entries.
- Preserve camera and gallery results across app locking so accepted photos return to and import into the active entry without another PIN prompt.

## 0.1.2

- Re-lock the diary whenever Mneme leaves the foreground, including when the user returns to the Home screen.
- Give written calendar days without photos their own clear, page-like preview treatment.
- Add a production-ready root Docker Compose stack and attach it, with its environment template, to GitHub releases.

## 0.1.1

- Upgrade `golang.org/x/crypto` to the patched 0.52.0 release.
- Add MIT licensing to the source, GitHub releases, and server container.
- Add full-history secret scanning and pin every GitHub Action to an immutable commit.
- Enable GitHub secret push protection, private vulnerability reporting, and Dependabot updates.

## 0.1.0

- Native Android diary with rich text, editable daily entries, photos, and full metadata.
- Journal, chronological list, continuous month, two-column media, and interactive map views.
- Location search with MapLibre/OpenFreeMap maps.
- PIN and biometric app lock, themes, encrypted backups, and automatic backup scheduling.
- GitHub release checks for the Android app and connected-server compatibility warnings.
- Docker Compose deployment for Mneme and an optional Cloudflare Tunnel.
