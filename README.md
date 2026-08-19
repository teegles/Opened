# Opened

Opened is a small, private Android app that counts how often a foldable phone is
unfolded and compares time spent folded with time spent open.

The app reads Android's standard hinge-angle sensor. Tracking happens locally
on the phone, requires no account, and does not send data over the internet.

## Current features

- Daily unfold count
- Daily screen-on time while folded and open
- Open share of total screen-on time
- Visual open-versus-folded percentage bar
- Seven-day screen-time history
- Material You design with wallpaper-derived dynamic color and system dark mode
- Persistent tracking with a visible Android notification
- Automatic restart after a device reboot
- Local-only storage

## Device compatibility

Android manufacturers decide whether to expose a standard hinge-angle sensor.
Opened checks for that sensor at runtime instead of relying on a manufacturer
or model name.

| Manufacturer | Device | Status |
| --- | --- | --- |
| OnePlus | Open (CPH2551), OxygenOS 16.0.3 | Confirmed |
| Google | Pixel Fold series | Needs testing |
| Samsung | Galaxy Z Fold and Z Flip series | Needs testing |
| Motorola | Razr series | Needs testing |
| Other Android foldables | — | Needs testing |

If you own an untested foldable, please try the app and open a device report.
Do not assume that an unlisted model is unsupported; it may simply not have
been tested yet.

## Install the APK

### [Download the latest Opened APK](https://github.com/teegles/Opened/releases/download/v0.2.2-alpha/Opened-v0.2.2-alpha.apk)

No development tools are required. Download the APK directly on your Android
phone, open the downloaded file, and approve installation from your browser or
file manager if Android asks.

If the direct download does not start, open the
[v0.2.2 alpha release page](https://github.com/teegles/Opened/releases/tag/v0.2.2-alpha),
expand **Assets**, and select `Opened-v0.2.2-alpha.apk`.

The current APK is an early testing release. Android and your browser may warn
that it came from outside the Play Store. The APK is approximately 28 MB and
has this SHA-256 checksum:

```text
aa2f0ce1e2fc81594f8df58b1649a50e62dfb7d2dedc0e1b3c21f6afec7f9d64
```

## Build from source

Requirements:

- Android Studio with Android SDK 37
- JDK 17 or newer (Android Studio includes a compatible runtime)
- An Android device running Android 12 or newer

Steps:

1. Clone this repository.
2. Open the repository folder in Android Studio.
3. Allow the Gradle sync to finish.
4. Enable USB debugging on the Android device and connect it.
5. Select the device and click **Run**.

You can also build a debug APK from a terminal:

```bash
./gradlew assembleDebug
```

The APK will be written to
`app/build/outputs/apk/debug/app-debug.apk`.

## How tracking works

Opened listens for `Sensor.TYPE_HINGE_ANGLE`, the standard Android sensor for
the angle between hinged sections of a device. Angles at or below 15 degrees
are treated as folded, and angles at or above 165 degrees are treated as open.
The gap between those thresholds prevents partially open movement from being
counted as repeated fold events.

Physical unfolds are counted regardless of whether the display is on. Folded
and open durations accumulate only while Android reports the device as
interactive, so leaving the phone open with its screen off does not distort the
usage percentage. Always-on display time is not included.

Android requires a foreground service and a persistent notification for this
kind of continuous background tracking. Some manufacturers apply additional
battery restrictions, so behavior must be verified on each device family.

## Privacy

Opened requests no internet, location, contacts, camera, or microphone
permission. Fold statistics are stored in Android `SharedPreferences` inside
the app's private storage. Uninstalling the app removes that data unless the
operating system restores it from a device backup.

Opened does not request Android Usage Access and does not inspect which apps
you use. It tracks only the fold state, screen-interactive state, and locally
aggregated durations needed for the dashboard.

## Contributing

Device test reports, bug fixes, accessibility improvements, translations, and
design suggestions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Opened is available under the [MIT License](LICENSE). You may use, modify,
redistribute, sublicense, or sell copies under its terms.
