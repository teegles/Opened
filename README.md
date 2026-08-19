# Fold Count

Fold Count is a simple Android app that counts how often you unfold your phone and
compares screen-on time while it is opened versus closed. Everything is stored
locally on your phone.

It was made and tested on the **OnePlus Open** running OxygenOS 16.0.3. It uses
Android's standard hinge-angle sensor, so it may work on other foldables, but
those devices have not been tested yet. If you have one, you are welcome to try
it and share how it goes.

## Install

### [Download the latest APK](https://github.com/teegles/Fold-Count/releases/download/v0.2.4-alpha/Fold-Count-v0.2.4-alpha.apk)

Download the APK on your phone, open it, and allow installation from your
browser or file manager if Android asks.

## Build from source

Open this project in Android Studio, or run:

```bash
./gradlew assembleDebug
```

The APK will be created at `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

Fold Count has no internet, location, or app-usage access. Android requires a
persistent notification so tracking can continue in the background.

## License

[MIT](LICENSE) — use, modify, and share it however you like.
