# Releasing Mneme

Mneme uses one semantic version for the Android app and server. Pushing a tag
such as `v0.2.0` starts `.github/workflows/release.yml`, which:

1. runs the Android and Go tests;
2. builds a signed, versioned APK;
3. publishes `lainsmain/mneme-server:0.2.0` and `:latest` for amd64/arm64;
4. creates a GitHub release and attaches the APK and MIT license.

Configure these encrypted GitHub Actions secrets before tagging:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `DOCKERHUB_TOKEN`

Keep an offline copy of the Android keystore and passwords. Every future APK
must use the same key or Android will refuse to install it as an update.

Create a release after reviewing the changelog:

```bash
git tag -a v0.2.0 -m "Mneme 0.2.0"
git push origin v0.2.0
```

The app reads GitHub's latest published release at launch and from the manual
Settings button. GitHub cannot wake an offline Android app, so checks occur when
Mneme runs rather than through a permanent push-notification service.
