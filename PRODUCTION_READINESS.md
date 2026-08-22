# Production readiness audit

Audit date: August 21, 2026

## Current result

Fold Count is small enough to keep its current architecture. Its previous APK
size came from publishing a debug build, not from unusually large app code.

| Artifact | Before optimization | After R8/resource shrinking |
| --- | ---: | ---: |
| APK | 22.6 MB unsigned release | 1.79 MB unsigned release |
| Android App Bundle | Not configured | 2.50 MB unsigned bundle |
| Published debug APK | 29.3 MB | Debug builds intentionally remain large |

## Completed hardening

- Enabled R8 optimization and resource shrinking for release builds.
- Added release APK and Android App Bundle build paths.
- Added optional, gitignored permanent-signing configuration.
- Added automated tests for transition counts, screen-off behavior, midnight
  splitting, lifetime totals, reset, migration, and service restart timing.
- Prevented service restarts from counting time while the process was absent.
- Added one-minute persistence checkpoints to limit data loss after a hard kill.
- Disabled cloud backup and device-transfer backup for locally stored statistics.
- Declared the required hinge-angle hardware feature for store filtering.
- Added a public privacy policy and an in-app link to it.
- Improved selector accessibility semantics and notification update behavior.
- Removed obsolete resources and unnecessary pre-Android-12 theme branches.

## Required before the first permanent release

1. Decide whether existing debug-build statistics need export/import support.
   Without it, current testers must uninstall the debug build and lose their
   statistics before installing the permanently signed build.
2. Generate the permanent app-signing keystore, record its certificate
   fingerprints, and make at least two encrypted backups.
3. Build and verify the signed APK and AAB using `RELEASING.md`.
4. Install the optimized signed APK on the OnePlus Open and Galaxy Z Flip7 and
   verify tracking, reboot restart, notification permission, and dark/light UI.
5. Preserve the R8 mapping file alongside every optimized release.

## Store work

- Prepare at least two current screenshots, a 512 px store icon, descriptions,
  contact details, and a content-rating questionnaire.
- Complete the Data safety form as no data collected or shared.
- Submit the `specialUse` foreground-service declaration with a short video
  showing Start tracking, the live notification, fold detection, and Stop
  tracking. This declaration is the primary Play review risk.
- Use target API 36 for the first submission. The project compiles against API
  37, so lint's "old target" advisory is intentional until API 37 targeting is
  required and its behavior has been tested.

## Non-blocking follow-ups

- Convert the approved square launcher artwork into a proper adaptive and
  monochrome icon after visual approval. The current icon works but triggers an
  advisory because its background fills the legacy square canvas.
- Move hard-coded English UI text into string resources if translations become
  a goal.
- Update Gradle and coroutine dependencies separately after their release notes
  and device behavior are reviewed; neither update is required for publishing.
- Manual wall-clock changes can distort the current interval. Automatic time,
  time-zone, and daylight-saving changes are bounded by one-minute checkpoints.
- Manufacturer battery management can still interrupt tracking despite the
  foreground service; this needs device-specific testing and documentation.
