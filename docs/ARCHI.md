# VR3D Mobile Architecture

## Scope

VR3D Mobile is a new project. It must not modify `E:\skills-main\skills\.results\VR3D_v1`.
It may read the existing Video-Depth-Anything source and weights from
`C:\Users\user\Documents\VR3D_v1_runtime`.

## Components

- `pc/`: Python FastAPI service, Tkinter operations GUI, security gates, depth inference,
  35-view synthesis, package creation, retention cleanup, and tunnel/GitHub endpoint control.
- `web/`: static bilingual GitHub Pages client. It discovers the current API from
  `endpoint.json`, uploads one validated image, polls job state, and downloads `.vr3d`.
- `android/`: Kotlin Android Studio project. It imports `.vr3d`, verifies the manifest and
  hashes, stores projects locally, and maps rotation-vector pitch/roll to interpolated views.
- `contracts/`: versioned package and API schemas shared by tests and implementations.

## Security invariants

1. The public server is fail-closed. It does not accept uploads until all mandatory gate
   health checks pass.
2. Allowed upload formats are JPEG, PNG, and WebP, verified by signature and full decode,
   not extension or MIME alone.
3. Limits are 25 MiB, 50 megapixels, one active job per client IP, and three jobs per hour.
4. Uploaded files enter a quarantine directory. V3 real-time protection must be running,
   an explicit local scanner must return a verified clean result, and a content-safety gate
   must pass before a sanitized pixel-only re-encode reaches the depth pipeline.
5. A failed or unverifiable gate deletes the quarantined payload immediately. Logs retain
   only a reason code and never thumbnails, image bytes, secrets, or full client IPs.
6. Inputs and results expire after 24 hours. Cleanup is covered by behavioral tests.
7. GitHub and tunnel credentials are never committed or written to logs.

## Rendering contract

- Depth: Video-Depth-Anything `infer_video_depth_one`, selectable `vitl` (default) or `vits`.
- Views: roll −12..+12 degrees in seven columns and pitch −8..+8 degrees in five rows.
- Output: longest edge at most 1920 px, WebP quality 90, 16-bit PNG depth map.
- Renderer: two-dimensional forward projection with a near-depth z-buffer, conservative
  edge handling, hole repair, and safe crop. Hidden scene geometry cannot be recovered.
- Package: ZIP container with `.vr3d` extension, `manifest.json`, sanitized source,
  `depth.png`, `views/*.webp`, and SHA-256 hashes.

## GUI concurrency

Tkinter owns the UI thread. Workers publish immutable events to a queue; the UI drains the
queue through `after`. All child server/tunnel processes are terminated on every exit path.
`pythonw` startup must provide safe `stdout`/`stderr` streams before importing libraries.

## Verification gates

- Python: compile, lint/type checks when available, unit tests, API integration test, real
  local depth smoke when the existing CUDA runtime is available.
- Web: static validation plus browser upload/status/download flow against the local API.
- Android: unit tests, lint, assembleDebug, APK signature inspection, and installation/sensor
  smoke on a connected Android 10+ device when available.
- Safety: disguised files, corrupt images, decompression bombs, traversal, scanner-off,
  unsafe-content fixtures, rate limits, hash mismatch, and retention cleanup must block.
