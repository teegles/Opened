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
- Optional list of apps used while the phone is open
- Automatic light and dark themes that follow the Android system setting
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

App-level detail is optional. If enabled, Android's Usage Access lets Opened
identify the foreground app. Opened records that detail only while the phone is
open and its screen is interactive. Package names and accumulated durations
stay in the app's private local storage and are never transmitted.

## Contributing

Device test reports, bug fixes, accessibility improvements, translations, and
design suggestions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Opened is available under the [MIT License](LICENSE). You may use, modify,
redistribute, sublicense, or sell copies under its terms.
