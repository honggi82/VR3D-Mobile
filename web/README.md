# VR3D Mobile web client

This directory is a dependency-free GitHub Pages client for the API described in
`../contracts/api-v1.md`. It is intentionally offline by default: the PC operator GUI must
replace `endpoint.json` with an online HTTPS API address after all mandatory safety gates pass.

Example online endpoint document:

```json
{
  "api_base": "https://random-name.trycloudflare.com",
  "online": true,
  "updated_at": "2026-08-12T10:00:00Z",
  "expires_at": null,
  "api_version": "v1"
}
```

For a local browser smoke test, serve the repository root rather than opening `index.html`
directly. The client accepts an HTTP API only when both the page and API use localhost.

Run the dependency-free checks with:

```powershell
node --test web/test/core.test.mjs
```

The browser checks the file size, extension, MIME type when supplied, and binary signature.
Those checks are convenience feedback only; the PC server remains the security boundary and
must independently decode, scan, sanitize, and content-check every upload.
