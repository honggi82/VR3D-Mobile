# VR3D Mobile for Android

Native Kotlin Android viewer for `.vr3d` packages. It targets Android 16 (API 36), supports Android 10+ (API 29), and uses only Android platform APIs at runtime.

## Open and build

1. Open this `android` directory in Android Studio.
2. Select a JDK 17 Gradle runtime and install/select Android SDK 36.
3. Run the `app` configuration, or run `testDebugUnitTest lintDebug assembleDebug` with Gradle 8.11.1.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The complete Gradle wrapper is included.

## Viewer behavior

- Imports `.vr3d` through the system file picker or `ACTION_VIEW`.
- Rejects non-v1 manifests, unknown schema keys, unsafe/duplicate ZIP paths, unexpected entries, size inconsistencies, excessive expansion ratios, and SHA-256 mismatches.
- Stores verified projects in private app storage and supports local deletion.
- Uses the rotation-vector sensor, falling back to the accelerometer when necessary.
- Calibrates the current pose with **Center**, clamps roll to ±12° and pitch to ±8°, and bilinearly crossfades the nearest grid views.
- Keeps at most five nearby RGB565 views decoded; stale queued decode work is skipped and all sensor/decoder resources are released with the Activity lifecycle.
- Follows the device language (English/Korean) until the in-app language toggle is used.

## Tests

The JVM tests cover strict manifest parsing, the fixed 7×5 grid, interpolation weights and bounds, valid extraction, ZIP traversal blocking, and hash mismatch cleanup. A real-device check is still required for sensor axis feel, display rotation, memory behavior, and file-provider interoperability.
