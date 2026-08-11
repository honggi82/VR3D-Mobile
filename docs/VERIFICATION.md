# Verification Report — 2026-08-12

## Verified

- PC: `python -m compileall -q vr3d_pc tests` passed.
- PC: `python -m pytest -q` passed, 16 tests; one non-blocking Starlette/httpx
  deprecation warning.
- Real depth: untouched VR3D_v1 runtime, CUDA, `vits`, 96×64 RGB input produced a finite
  float32 64×96 map normalized to `[0, 1]`.
- Cross-component package: real depth plus 35 rendered views produced a 38-entry `.vr3d`
  ZIP (manifest plus 37 hashed payloads), validated against package schema v1.
- Android: the strict Kotlin importer accepted that PC-generated package fixture.
- Android: `testDebugUnitTest` passed 10/10; `lintDebug` completed with 0 errors and
  2 non-blocking warnings; `assembleDebug` succeeded.
- APK: package `io.github.honggi82.vr3dmobile`, version 1.0.0, minSdk 29, targetSdk 36;
  debug APK Signature Scheme v2 verification passed.
- Web: Node tests passed 7/7. Chromium showed the offline state with the create button
  disabled, rejected a text payload disguised as PNG, and reported 0 console errors.
- Safety: live V3 process health passed; explicit scanner and content commands are
  unconfigured, therefore live `publicReady` is false and the tracked endpoint is offline.
- Retention, rate limiting, failed-quarantine deletion, ZIP traversal, compression ratio,
  strict manifest keys, and SHA-256 mismatch behavior are covered by automated tests.

## Not verified / intentionally blocked

- No physical Android device is connected, so APK installation, real sensor axes,
  orientation feel, memory behavior, and Android file association are not device-tested.
- `cloudflared` is not installed and Quick Tunnel was not opened.
- No explicit scanner or content-safety executable is configured; public upload must remain
  disabled until both are installed, configured, and tested with safe and blocking fixtures.
- The Tkinter GUI was code- and helper-tested but not visually exercised in this session.
- GitHub repository creation, Pages publication, endpoint commit, and push were not performed;
  those are outward-facing actions requiring explicit approval and valid GitHub authentication.

## Artifact

- APK SHA-256: `03747351C61E66219F94DC882EF3889B5A0F603A78F903D2B0DD8EA6BA8CFE46`
