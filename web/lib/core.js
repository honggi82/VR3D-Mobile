export const MAX_FILE_BYTES = 25 * 1024 * 1024;

const FORMATS = {
  jpeg: {
    extensions: ["jpg", "jpeg"],
    mimeTypes: ["image/jpeg"],
  },
  png: {
    extensions: ["png"],
    mimeTypes: ["image/png"],
  },
  webp: {
    extensions: ["webp"],
    mimeTypes: ["image/webp"],
  },
};

export function detectImageFormat(bytes) {
  if (!(bytes instanceof Uint8Array)) {
    bytes = new Uint8Array(bytes);
  }

  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return "jpeg";
  }

  const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (bytes.length >= png.length && png.every((value, index) => bytes[index] === value)) {
    return "png";
  }

  if (
    bytes.length >= 12 &&
    ascii(bytes, 0, 4) === "RIFF" &&
    ascii(bytes, 8, 12) === "WEBP"
  ) {
    return "webp";
  }

  return null;
}

function ascii(bytes, start, end) {
  return String.fromCharCode(...bytes.slice(start, end));
}

export function validateFileMeta(file, detectedFormat) {
  if (!file || typeof file.name !== "string" || typeof file.size !== "number") {
    return { ok: false, code: "file_missing" };
  }
  if (file.size <= 0) {
    return { ok: false, code: "file_empty" };
  }
  if (file.size > MAX_FILE_BYTES) {
    return { ok: false, code: "file_too_large" };
  }
  if (!detectedFormat || !FORMATS[detectedFormat]) {
    return { ok: false, code: "signature_invalid" };
  }

  const extension = file.name.includes(".")
    ? file.name.split(".").pop().toLowerCase()
    : "";
  const format = FORMATS[detectedFormat];
  if (!format.extensions.includes(extension)) {
    return { ok: false, code: "extension_mismatch" };
  }
  if (file.type && !format.mimeTypes.includes(file.type.toLowerCase())) {
    return { ok: false, code: "mime_mismatch" };
  }
  return { ok: true, format: detectedFormat };
}

export function normalizeEndpoint(documentValue, pageUrl = "https://example.invalid/") {
  if (!documentValue || documentValue.online !== true) {
    throw new Error("endpoint_offline");
  }
  if (documentValue.api_version && documentValue.api_version !== "v1") {
    throw new Error("endpoint_version");
  }
  if (documentValue.expires_at) {
    const expiresAt = Date.parse(documentValue.expires_at);
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      throw new Error("endpoint_offline");
    }
  }
  const candidate =
    documentValue.api_base ??
    documentValue.api_base_url ??
    documentValue.apiBaseUrl ??
    documentValue.url;
  if (typeof candidate !== "string" || candidate.trim() === "") {
    throw new Error("endpoint_invalid");
  }

  const endpoint = new URL(candidate);
  const page = new URL(pageUrl);
  const localHostnames = new Set(["localhost", "127.0.0.1", "::1"]);
  const secure = endpoint.protocol === "https:";
  const localDevelopment =
    endpoint.protocol === "http:" &&
    localHostnames.has(endpoint.hostname) &&
    localHostnames.has(page.hostname);
  if (!secure && !localDevelopment) {
    throw new Error("endpoint_insecure");
  }
  if (endpoint.username || endpoint.password) {
    throw new Error("endpoint_invalid");
  }

  endpoint.pathname = endpoint.pathname.replace(/\/$/, "");
  endpoint.search = "";
  endpoint.hash = "";
  return endpoint.toString().replace(/\/$/, "");
}

export function apiUrl(baseUrl, path) {
  return `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

export function jobUrls(baseUrl, response) {
  const jobId = response?.jobId ?? response?.job_id ?? response?.id;
  if (typeof jobId !== "string" || jobId.trim() === "") {
    throw new Error("job_response_invalid");
  }
  return {
    id: jobId,
    status: absoluteOrApi(baseUrl, response.status_url, `/api/v1/jobs/${encodeURIComponent(jobId)}`),
    download: absoluteOrApi(
      baseUrl,
      response.download_url,
      `/api/v1/jobs/${encodeURIComponent(jobId)}/download`,
    ),
  };
}

function absoluteOrApi(baseUrl, candidate, fallbackPath) {
  if (typeof candidate !== "string" || candidate.trim() === "") {
    return apiUrl(baseUrl, fallbackPath);
  }
  const resolved = new URL(candidate, `${baseUrl}/`);
  if (resolved.origin !== new URL(baseUrl).origin) {
    return apiUrl(baseUrl, fallbackPath);
  }
  return resolved.toString();
}

export function normalizeJobState(payload) {
  const status = String(payload?.status ?? payload?.state ?? "queued").toLowerCase();
  const rawProgress = Number(payload?.progress ?? payload?.progress_percent ?? 0);
  const progress = Number.isFinite(rawProgress) ? Math.max(0, Math.min(100, rawProgress)) : 0;
  const completed = new Set(["completed", "complete", "succeeded", "ready"]);
  const failed = new Set(["failed", "rejected", "blocked", "cancelled", "expired"]);
  return {
    status,
    progress: completed.has(status) ? 100 : progress,
    terminal: completed.has(status) || failed.has(status),
    succeeded: completed.has(status),
    stage: String(payload?.stage ?? status),
    detailCode: String(payload?.errorCode ?? payload?.reason_code ?? payload?.error_code ?? ""),
    security: payload?.security ?? payload?.security_stages ?? {},
  };
}

export function safeDownloadName(contentDisposition, fallback = "result.vr3d") {
  const match = /filename\*?=(?:UTF-8''|\")?([^\";]+)/i.exec(contentDisposition ?? "");
  let candidate = fallback;
  if (match) {
    try {
      candidate = decodeURIComponent(match[1].trim());
    } catch {
      candidate = match[1].trim();
    }
  }
  candidate = candidate.replace(/[\\/:*?"<>|\u0000-\u001f]/g, "_").trim();
  return candidate.toLowerCase().endsWith(".vr3d") && candidate.length > 5
    ? candidate
    : fallback;
}
