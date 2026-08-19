# Contributing to Fold Count

Thank you for helping make Fold Count work across more foldable phones.

## Device testing

When reporting results, include:

- Manufacturer and exact model
- Android version and manufacturer software version
- Whether Fold Count detects closed and opened states
- Whether the unfold counter increments exactly once
- Whether tracking continues with the screen off
- Whether tracking survives a device reboot
- Any battery or auto-launch setting you had to change

Please do not include device serial numbers, account details, or other personal
information.

## Code changes

1. Fork the repository and create a focused branch.
2. Keep sensor behavior vendor-neutral when possible.
3. Build the debug app with `./gradlew assembleDebug`.
4. Test state changes while folded, partially open, and fully open.
5. Open a pull request explaining the behavior and devices tested.

Manufacturer-specific fallbacks are welcome when the standard Android sensor
is unavailable, but they should remain isolated from the core tracking logic.
