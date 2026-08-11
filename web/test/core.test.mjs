import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  MAX_FILE_BYTES,
  detectImageFormat,
  jobUrls,
  normalizeEndpoint,
  normalizeJobState,
  safeDownloadName,
  validateFileMeta,
} from "../lib/core.js";

test("detects JPEG, PNG, and WebP signatures", () => {
  assert.equal(detectImageFormat(Uint8Array.from([0xff, 0xd8, 0xff, 0xe0])), "jpeg");
  assert.equal(detectImageFormat(Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])), "png");
  assert.equal(detectImageFormat(new TextEncoder().encode("RIFF1234WEBP")), "webp");
  assert.equal(detectImageFormat(new TextEncoder().encode("not an image")), null);
});

test("validates size, extension, MIME, and detected signature together", () => {
  assert.deepEqual(validateFileMeta({ name: "photo.jpg", size: 12, type: "image/jpeg" }, "jpeg"), { ok: true, format: "jpeg" });
  assert.equal(validateFileMeta({ name: "photo.png", size: 12, type: "image/png" }, "jpeg").code, "extension_mismatch");
  assert.equal(validateFileMeta({ name: "photo.jpg", size: 12, type: "image/png" }, "jpeg").code, "mime_mismatch");
  assert.equal(validateFileMeta({ name: "photo.jpg", size: MAX_FILE_BYTES + 1, type: "image/jpeg" }, "jpeg").code, "file_too_large");
});

test("endpoint discovery is fail-closed and requires HTTPS outside localhost", () => {
  assert.equal(normalizeEndpoint({ online: true, api_base: "https://node.example/api/" }), "https://node.example/api");
  assert.throws(() => normalizeEndpoint({ online: false, api_base: "https://node.example" }), /endpoint_offline/);
  assert.throws(() => normalizeEndpoint({ online: true, api_base: "http://node.example" }), /endpoint_insecure/);
  assert.throws(() => normalizeEndpoint({ online: true, api_base: "https://node.example", api_version: "v2" }), /endpoint_version/);
  assert.throws(() => normalizeEndpoint({ online: true, api_base: "https://node.example", expires_at: "2000-01-01T00:00:00Z" }), /endpoint_offline/);
  assert.equal(normalizeEndpoint({ online: true, api_base: "http://localhost:8000" }, "http://localhost:8080/"), "http://localhost:8000");
});

test("derives v1 status and download URLs from a camel-case upload response", () => {
  const urls = jobUrls("https://node.example", { jobId: "3f8a" });
  assert.equal(urls.status, "https://node.example/api/v1/jobs/3f8a");
  assert.equal(urls.download, "https://node.example/api/v1/jobs/3f8a/download");
  const untrusted = jobUrls("https://node.example", { jobId: "3f8a", download_url: "https://other.example/result" });
  assert.equal(untrusted.download, "https://node.example/api/v1/jobs/3f8a/download");
});

test("normalizes contract job status and caps progress", () => {
  assert.deepEqual(normalizeJobState({ status: "complete", progress: 101 }), {
    status: "complete", progress: 100, terminal: true, succeeded: true, stage: "complete", detailCode: "", security: {},
  });
  const failed = normalizeJobState({ status: "failed", progress: 30, errorCode: "scanner_rejected" });
  assert.equal(failed.terminal, true);
  assert.equal(failed.detailCode, "scanner_rejected");
  assert.equal(normalizeJobState({ status: "processing", progress: 1 }).progress, 1);
});

test("sanitizes package download names", () => {
  assert.equal(safeDownloadName('attachment; filename="scene.vr3d"'), "scene.vr3d");
  assert.equal(safeDownloadName('attachment; filename="../scene.exe"'), "result.vr3d");
});

test("page has no external assets and ships an offline endpoint by default", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const endpoint = JSON.parse(await readFile(new URL("../endpoint.json", import.meta.url), "utf8"));
  assert.doesNotMatch(html, /(?:src|href)=["']https?:\/\//i);
  assert.match(html, /Content-Security-Policy/);
  assert.equal(endpoint.online, false);
  assert.equal(endpoint.api_base, null);
});
