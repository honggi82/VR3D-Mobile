# Implementation Notes

This ledger records deviations from `design_map.html` and `docs/PLAN.md`.

## 2026-08-12

- Plan: use the TRIP implementation scripts. Territory: this is a new repository without the
  TRIP `.claude` script bundle or an existing `docs/ARCHI.md`. Decision: create the architecture
  and plan documents here, use a dedicated Git branch, and apply the same self-review and test
  gates without fabricating missing TRIP state.
- Plan: build the APK from Android Studio. Territory: Android Studio is not installed, while the
  same Android Gradle Plugin 8.10.1 toolchain, SDK 36, Gradle 8.14, and JDK 17 are locally
  available. Decision: keep a standard Android Studio project and produce the APK with that
  local Gradle toolchain; record device/IDE-only checks separately.
- Plan: use the default Android path validation. Territory: the workspace has Korean characters
  and the Android Gradle Plugin blocks such Windows paths before compilation. Decision: enable
  `android.overridePathCheck=true`; all build/test outputs remain inside this project.
- Plan: run FastAPI and Video-Depth-Anything in one Python environment. Territory: the system
  Python has FastAPI but lacks `easydict`, while the untouched VR3D_v1 runtime has all VDA
  dependencies but lacks FastAPI. Decision: keep VR3D_v1 unchanged and isolate inference in a
  child worker launched with its existing virtual-environment Python.
- Plan: automatically update the published GitHub Pages endpoint. Territory: writing the local
  tracked `web/endpoint.json` is implemented, but committing or pushing it is an outward-facing
  publication requiring repository authentication and explicit approval. Decision: keep the
  checked-in endpoint offline and defer the GitHub push step; public upload remains unavailable.
- Plan: run Android tests directly in the Korean workspace path. Territory: AGP compilation works
  there after overriding its path check, but Gradle's Windows JUnit worker then loses the compiled
  test classes. Decision: perform verified builds through a temporary ASCII `R:` drive mapping to
  the same project and remove that mapping immediately after the build.
- Plan: use an `.mjs` browser module. Territory: the local Windows Python static server labels
  `.mjs` as `text/plain`, so the browser refuses to execute the page during end-to-end testing.
  Decision: rename the same ES module to `.js` under the package's `type: module` setting, which
  is also compatible with GitHub Pages.
- Plan: rely only on independently generated PC and Android fixtures. Territory: that leaves the
  cross-platform package boundary unexercised. Decision: keep one small VDA/PC-generated
  `.vr3d` as an Android JVM test resource and require the strict Android importer to accept it.
- Plan: rate-limit by the socket address. Territory: Quick Tunnel connects to localhost, so all
  public users would share one rate bucket. Decision: bind the API to localhost and prefer a
  syntactically valid Cloudflare `CF-Connecting-IP` value for the per-client limiter.
