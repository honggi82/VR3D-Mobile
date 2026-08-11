# VR3D Mobile API v1

All routes are below `/api/v1`. JSON error responses use
`{"detail":{"code":"stable_code"}}`; no response contains a client IP or local path.

## `GET /health`

`200` response:

```json
{
  "status": "online",
  "publicReady": false,
  "gates": {
    "v3": {"ready": true, "code": "ok"},
    "scanner": {"ready": false, "code": "scanner_unconfigured"},
    "content": {"ready": false, "code": "content_unconfigured"}
  }
}
```

`publicReady` is true only when every mandatory gate is healthy.

## `POST /jobs`

Multipart fields: `file` (one JPEG, PNG, or WebP) and `quality` (`vitl`, default, or
`vits`). Limits are 25 MiB, 50 megapixels, one active job per client and three accepted
jobs per rolling hour. The original filename must be a basename without path separators.

`202` response:

```json
{"jobId":"UUID","status":"queued","createdAt":"RFC3339 timestamp"}
```

The endpoint fails with `503 gates_unavailable`, `400 invalid_filename`,
`400 invalid_quality`, `413 file_size`, `415 invalid_image` (signature), `409 active_limit`,
or `429 hourly_limit`. To ensure untrusted bytes reach the explicit scanner before a decoder,
full decode and pixel-limit failures are reported through the later `failed` job state. A
payload that fails any scanner, decode, or content gate has its quarantined bytes deleted
immediately.

## `GET /jobs/{jobId}`

`200` response:

```json
{
  "jobId":"UUID",
  "status":"queued|scanning|processing|complete|failed",
  "progress":0,
  "createdAt":"RFC3339 timestamp",
  "expiresAt":"RFC3339 timestamp",
  "errorCode":null,
  "downloadUrl":null
}
```

`progress` is an integer from 0 through 100. `downloadUrl` is present only for a complete
job and equals `/api/v1/jobs/{jobId}/download`. Unknown or expired IDs return `404 job_not_found`.

## `GET /jobs/{jobId}/download`

Returns `application/vnd.vr3d+zip` with a `.vr3d` filename only for complete, unexpired
jobs. Other states return `409 job_not_complete`; unknown/expired IDs return
`404 job_not_found`.
