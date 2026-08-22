# Releasing Fold Count

Fold Count uses one permanent signing identity for GitHub APKs and any future
app-store builds. Never commit the keystore, its passwords, or
`keystore.properties`.

## One-time signing setup

Create the key with the JDK `keytool`. Omitting password arguments keeps the
passwords out of shell history:

```bash
keytool -genkeypair -v \
  -keystore fold-count-release.jks \
  -alias fold-count \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep at least two encrypted backups of the keystore and passwords in separate
locations. Losing the key means losing the ability to publish compatible
updates outside stores that manage the signing key.

Copy `keystore.properties.example` to `keystore.properties`, fill in the
absolute keystore path and credentials, and leave both local files untracked.

## Verify and build

Increase `versionCode` for every published build and choose an appropriate
`versionName`. Then run:

```bash
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleRelease bundleRelease
```

Outputs:

- Signed APK: `app/build/outputs/apk/release/app-release.apk`
- Signed app bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

Preserve the mapping file for every public release so crash traces from the
optimized build can be decoded.

Verify the APK before uploading it:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

The first permanently signed release cannot update the old debug-signed APK.
Current testers must uninstall the debug build first. Every later APK must use
the same permanent key.

If Fold Count is eventually enrolled in Play App Signing, provide this app
signing key rather than asking Google to generate a different one. Use a
separate upload key for Play Console submissions.
