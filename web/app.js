import {
  apiUrl,
  detectImageFormat,
  jobUrls,
  normalizeEndpoint,
  normalizeJobState,
  safeDownloadName,
  validateFileMeta,
} from "./lib/core.js";

const copy = {
  ko: {
    skip: "본문으로 건너뛰기", checking: "서버 확인 중", online: "서버 온라인", offline: "서버 오프라인",
    gatesBlocked: "안전 게이트 대기 중", skipTitle: "",
    heroTitle: "사진 한 장을<br><span>움직이는 장면</span>으로.",
    heroBody: "사진을 올리면 사용자의 PC에서 깊이와 35개 시점을 계산해 Android용 VR3D 패키지를 만듭니다.",
    views: "시점", range: "기울기 범위", retention: "자동 삭제", choosePhoto: "사진 선택",
    dropTitle: "사진을 놓거나 선택하세요", dropHelp: "JPG · PNG · WebP / 최대 25 MiB",
    removeFile: "선택한 사진 제거", chooseQuality: "처리 품질", highQuality: "고품질", recommended: "권장",
    fastMode: "빠른 처리", securityTitle: "안전 검사 후에만 처리합니다.",
    securityBody: "V3 상태, 추가 백신, 이미지 재인코딩과 콘텐츠 안전 검사를 모두 통과해야 합니다.",
    create: "VR3D 만들기", offlineHelp: "PC 처리 서버가 오프라인입니다. 운영자가 서버를 시작하면 자동으로 연결됩니다.",
    processingLabel: "SECURE PROCESSING", processing: "VR3D 생성 중", stageUpload: "업로드 검증",
    stageV3: "AhnLab V3 상태", stageScanner: "추가 로컬 검사", stageSanitize: "안전한 이미지 재인코딩",
    stageContent: "콘텐츠 안전 검사", stageDepth: "깊이 계산", stageViews: "35개 시점 생성",
    stagePackage: "VR3D 패키지 완성", blockedTitle: "처리가 완료되지 않았습니다.",
    readyTitle: "VR3D 패키지가 준비되었습니다.", readyBody: "24시간 안에 다운로드해 Android 앱에서 여세요.",
    download: ".vr3d 다운로드", privateProcessing: "사용자 PC에서 처리",
    noPermanentStorage: "입력과 결과는 24시간 후 자동 삭제", testService: "Quick Tunnel 기반 시험 서비스",
    footer: "단일 사진의 가려진 영역은 완전히 복원할 수 없습니다.",
    queued: "안전한 처리 대기 중", scanning: "V3 및 보안 검사 중", processingStage: "3차원 정보 생성 중",
    complete: "패키지 완성", failed: "안전 검사 또는 처리 실패", reconnecting: "서버 연결을 다시 확인합니다.",
    file_missing: "사진을 선택해 주세요.", file_empty: "빈 파일은 처리할 수 없습니다.",
    file_too_large: "파일은 25 MiB 이하여야 합니다.", signature_invalid: "JPG, PNG 또는 WebP 사진만 선택할 수 있습니다.",
    extension_mismatch: "파일 확장자와 실제 사진 형식이 일치하지 않습니다.",
    mime_mismatch: "브라우저가 보고한 파일 형식과 실제 사진 형식이 일치하지 않습니다.",
    endpoint_offline: "PC 처리 서버가 오프라인입니다.", endpoint_invalid: "서버 주소가 올바르지 않습니다.", endpoint_version: "지원하지 않는 API 버전입니다.",
    endpoint_insecure: "보안 HTTPS 주소가 아니어서 연결하지 않았습니다.", health_unavailable: "서버 상태를 확인할 수 없습니다.",
    upload_failed: "업로드 요청이 거부되었습니다.", status_failed: "작업 상태를 확인할 수 없습니다.",
    download_failed: "패키지를 다운로드할 수 없습니다.", network_error: "네트워크 연결을 확인해 주세요.",
    gates_unavailable: "필수 안전 검사가 준비되지 않았습니다.", invalid_filename: "파일 이름이 올바르지 않습니다.",
    invalid_quality: "처리 품질 설정이 올바르지 않습니다.", file_size: "서버의 파일 크기 제한을 초과했습니다.",
    invalid_image: "서버가 유효한 사진으로 확인하지 못했습니다.", active_limit: "이미 진행 중인 작업이 있습니다.",
    hourly_limit: "시간당 처리 한도에 도달했습니다. 잠시 후 다시 시도하세요.", job_not_found: "작업이 만료되었거나 존재하지 않습니다.",
    scanner_rejected: "로컬 백신 검사에서 파일이 차단되었습니다.", content_rejected: "콘텐츠 안전 검사에서 파일이 차단되었습니다.",
    unknown_error: "요청을 완료할 수 없습니다.",
  },
  en: {
    skip: "Skip to content", checking: "Checking server", online: "Server online", offline: "Server offline",
    gatesBlocked: "Security gates not ready", skipTitle: "",
    heroTitle: "Turn one photo into<br><span>a scene that moves.</span>",
    heroBody: "Upload a photo. Your PC calculates depth and 35 viewpoints, then creates a VR3D package for Android.",
    views: "views", range: "tilt range", retention: "auto deletion", choosePhoto: "Choose a photo",
    dropTitle: "Drop or choose a photo", dropHelp: "JPG · PNG · WebP / up to 25 MiB",
    removeFile: "Remove selected photo", chooseQuality: "Processing quality", highQuality: "High quality", recommended: "recommended",
    fastMode: "Fast mode", securityTitle: "Processing starts only after safety checks.",
    securityBody: "V3 status, a second local scanner, safe re-encoding, and content safety must all pass.",
    create: "Create VR3D", offlineHelp: "The PC processing server is offline. This page reconnects automatically when the operator starts it.",
    processingLabel: "SECURE PROCESSING", processing: "Creating VR3D", stageUpload: "Upload validation",
    stageV3: "AhnLab V3 status", stageScanner: "Second local scan", stageSanitize: "Safe image re-encode",
    stageContent: "Content safety", stageDepth: "Depth estimation", stageViews: "Generate 35 views",
    stagePackage: "Build VR3D package", blockedTitle: "Processing did not complete.",
    readyTitle: "Your VR3D package is ready.", readyBody: "Download it within 24 hours and open it in the Android app.",
    download: "Download .vr3d", privateProcessing: "Processed on the user's PC",
    noPermanentStorage: "Input and result auto-delete after 24 hours", testService: "Quick Tunnel test service",
    footer: "Occluded areas cannot be fully recovered from a single photo.",
    queued: "Waiting for secure processing", scanning: "Running V3 and security checks", processingStage: "Creating spatial information",
    complete: "Package complete", failed: "Safety check or processing failed", reconnecting: "Checking the server connection again.",
    file_missing: "Choose a photo first.", file_empty: "Empty files cannot be processed.",
    file_too_large: "The file must be 25 MiB or smaller.", signature_invalid: "Choose a JPG, PNG, or WebP photo.",
    extension_mismatch: "The filename extension does not match the actual image format.",
    mime_mismatch: "The browser file type does not match the actual image format.",
    endpoint_offline: "The PC processing server is offline.", endpoint_invalid: "The server address is invalid.", endpoint_version: "The advertised API version is unsupported.",
    endpoint_insecure: "The connection was refused because the API is not HTTPS.", health_unavailable: "The server health check failed.",
    upload_failed: "The upload request was rejected.", status_failed: "The job status is unavailable.",
    download_failed: "The package could not be downloaded.", network_error: "Check your network connection.",
    gates_unavailable: "Mandatory safety checks are not ready.", invalid_filename: "The filename is invalid.",
    invalid_quality: "The processing quality is invalid.", file_size: "The server file-size limit was exceeded.",
    invalid_image: "The server could not verify this as a valid image.", active_limit: "A job is already active for this client.",
    hourly_limit: "The hourly processing limit was reached. Try again later.", job_not_found: "This job is missing or has expired.",
    scanner_rejected: "The local scanner blocked this file.", content_rejected: "The content-safety check blocked this file.",
    unknown_error: "The request could not be completed.",
  },
};

const elements = {
  form: document.querySelector("#uploadForm"), input: document.querySelector("#photoInput"),
  dropZone: document.querySelector("#dropZone"), fileSummary: document.querySelector("#fileSummary"),
  fileName: document.querySelector("#fileName"), fileMeta: document.querySelector("#fileMeta"),
  preview: document.querySelector("#preview"), removeFile: document.querySelector("#removeFile"),
  fileError: document.querySelector("#fileError"), submit: document.querySelector("#submitButton"),
  serverStatus: document.querySelector("#serverStatus"), language: document.querySelector("#languageButton"),
  jobPanel: document.querySelector("#jobPanel"), jobId: document.querySelector("#jobId"),
  progressBar: document.querySelector("#progressBar"), progressTrack: document.querySelector(".progress-track"),
  progressText: document.querySelector("#progressText"), stageText: document.querySelector("#stageText"),
  jobError: document.querySelector("#jobError"), jobErrorText: document.querySelector("#jobErrorText"),
  jobReady: document.querySelector("#jobReady"), download: document.querySelector("#downloadButton"),
  stages: [...document.querySelectorAll(".stages li")],
};

const state = {
  language: navigator.language.toLowerCase().startsWith("ko") ? "ko" : "en",
  endpoint: null, serverReady: false, file: null, previewUrl: null, job: null,
  pollTimer: null, busy: false, serverStatusKind: "checking", serverStatusKey: "checking",
  jobState: null, jobErrorCode: null,
};

function t(key) { return copy[state.language][key] ?? copy.en[key] ?? key; }

function applyLanguage() {
  document.documentElement.lang = state.language;
  document.querySelectorAll("[data-i18n]").forEach((node) => { node.textContent = t(node.dataset.i18n); });
  document.querySelectorAll("[data-i18n-html]").forEach((node) => { node.innerHTML = t(node.dataset.i18nHtml); });
  document.querySelectorAll("[data-i18n-aria]").forEach((node) => { node.setAttribute("aria-label", t(node.dataset.i18nAria)); });
  elements.language.textContent = state.language === "ko" ? "EN" : "한국어";
  setServerStatus(state.serverStatusKind, state.serverStatusKey);
  if (state.jobState) updateJob(state.jobState);
  if (state.jobErrorCode) failJob(state.jobErrorCode);
  refreshButton();
}

function setServerStatus(kind, key) {
  state.serverStatusKind = kind;
  state.serverStatusKey = key;
  elements.serverStatus.className = `status-pill status-${kind}`;
  elements.serverStatus.querySelector("span:last-child").textContent = t(key);
  document.body.classList.toggle("server-online", kind === "online");
}

async function discoverEndpoint() {
  if (state.busy) return;
  try {
    const endpointResponse = await timedFetch(`./endpoint.json?ts=${Date.now()}`, { cache: "no-store" });
    if (!endpointResponse.ok) throw new Error("endpoint_offline");
    const documentValue = await endpointResponse.json();
    const endpoint = normalizeEndpoint(documentValue, window.location.href);
    const healthResponse = await timedFetch(apiUrl(endpoint, "/api/v1/health"), { cache: "no-store" });
    if (!healthResponse.ok) throw new Error("health_unavailable");
    const health = await healthResponse.json();
    const gatesReady = ["v3", "scanner", "content"].every((gate) => health.gates?.[gate]?.ready === true);
    state.endpoint = endpoint;
    state.serverReady = health.status === "online" && health.publicReady === true && gatesReady;
    setServerStatus(state.serverReady ? "online" : "offline", state.serverReady ? "online" : "gatesBlocked");
  } catch (error) {
    state.endpoint = null;
    state.serverReady = false;
    setServerStatus("offline", "offline");
  }
  refreshButton();
}

async function timedFetch(url, options = {}, milliseconds = 8000) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), milliseconds);
  try { return await fetch(url, { ...options, signal: controller.signal }); }
  finally { window.clearTimeout(timer); }
}

async function selectFile(file) {
  clearFileError();
  const header = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  const validation = validateFileMeta(file, detectImageFormat(header));
  if (!validation.ok) {
    clearSelectedFile();
    showFileError(validation.code);
    return;
  }
  clearSelectedFile();
  state.file = file;
  state.previewUrl = URL.createObjectURL(file);
  elements.preview.src = state.previewUrl;
  elements.preview.alt = file.name;
  elements.fileName.textContent = file.name;
  elements.fileMeta.textContent = `${validation.format.toUpperCase()} · ${formatBytes(file.size)}`;
  elements.dropZone.hidden = true;
  elements.fileSummary.hidden = false;
  refreshButton();
}

function clearSelectedFile() {
  if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
  state.file = null;
  state.previewUrl = null;
  elements.input.value = "";
  elements.preview.removeAttribute("src");
  elements.dropZone.hidden = false;
  elements.fileSummary.hidden = true;
  refreshButton();
}

function showFileError(code) {
  elements.fileError.textContent = t(code);
  elements.fileError.hidden = false;
}
function clearFileError() { elements.fileError.hidden = true; elements.fileError.textContent = ""; }
function refreshButton() { elements.submit.disabled = !state.file || !state.serverReady || state.busy; }
function formatBytes(bytes) { return `${(bytes / (1024 * 1024)).toFixed(bytes >= 1024 * 1024 ? 1 : 2)} MiB`; }

async function submitJob(event) {
  event.preventDefault();
  if (!state.file) return showFileError("file_missing");
  if (!state.endpoint || !state.serverReady || state.busy) return;
  state.busy = true;
  refreshButton();
  resetJobPanel();
  elements.jobPanel.hidden = false;
  elements.jobPanel.scrollIntoView({ behavior: "smooth", block: "start" });
  try {
    const form = new FormData();
    form.append("file", state.file, state.file.name);
    form.append("quality", new FormData(elements.form).get("model"));
    const response = await timedFetch(apiUrl(state.endpoint, "/api/v1/jobs"), { method: "POST", body: form }, 120000);
    if (!response.ok) throw new Error(await responseErrorCode(response, "upload_failed"));
    const payload = await response.json();
    state.job = jobUrls(state.endpoint, payload);
    elements.jobId.textContent = `JOB ${state.job.id.slice(0, 8).toUpperCase()}`;
    updateJob({ status: payload.status ?? "queued", progress: 0 });
    await pollJob();
  } catch (error) {
    failJob(errorCode(error, "network_error"));
    state.busy = false;
    refreshButton();
  }
}

async function pollJob() {
  if (!state.job) return;
  try {
    const response = await timedFetch(state.job.status, { cache: "no-store" });
    if (!response.ok) throw new Error(await responseErrorCode(response, "status_failed"));
    const payload = await response.json();
    const normalized = normalizeJobState(payload);
    updateJob(normalized);
    if (payload.downloadUrl) state.job.download = new URL(payload.downloadUrl, `${state.endpoint}/`).toString();
    if (normalized.succeeded) {
      state.busy = false;
      elements.jobReady.hidden = false;
      refreshButton();
      return;
    }
    if (normalized.terminal) {
      state.busy = false;
      failJob(normalized.detailCode || "unknown_error");
      refreshButton();
      return;
    }
    state.pollTimer = window.setTimeout(pollJob, 1500);
  } catch (error) {
    state.busy = false;
    failJob(errorCode(error, "status_failed"));
    refreshButton();
  }
}

function resetJobPanel() {
  window.clearTimeout(state.pollTimer);
  elements.jobError.hidden = true;
  elements.jobReady.hidden = true;
  elements.jobId.textContent = "";
  state.jobState = null;
  state.jobErrorCode = null;
  updateProgress(0);
  elements.stages.forEach((item) => { item.className = ""; });
}

function updateJob(job) {
  state.jobState = job;
  updateProgress(job.progress ?? 0);
  const status = job.status ?? "queued";
  elements.stageText.textContent = t(status === "processing" ? "processingStage" : status);
  const rank = stageRank(status, job.progress ?? 0);
  elements.stages.forEach((item, index) => {
    item.className = index < rank ? "is-done" : index === rank ? "is-active" : "";
  });
  if (status === "scanning") {
    elements.stages.slice(1, 5).forEach((item) => { item.className = "is-active"; });
  }
  if (status === "complete") elements.stages.forEach((item) => { item.className = "is-done"; });
}

function stageRank(status, progress) {
  if (status === "queued") return 0;
  if (status === "scanning") return 1;
  if (status === "processing") return progress < 60 ? 5 : progress < 90 ? 6 : 7;
  if (status === "complete") return 8;
  return Math.min(7, Math.max(0, Math.floor(progress / 13)));
}

function updateProgress(progress) {
  const safe = Math.max(0, Math.min(100, Number(progress) || 0));
  elements.progressBar.style.width = `${safe}%`;
  elements.progressTrack.setAttribute("aria-valuenow", String(Math.round(safe)));
  elements.progressText.textContent = `${Math.round(safe)}%`;
}

function failJob(code) {
  state.jobErrorCode = code;
  elements.jobReady.hidden = true;
  elements.jobErrorText.textContent = t(code) === code ? t("unknown_error") : t(code);
  elements.jobError.hidden = false;
  const active = elements.stages.find((item) => item.classList.contains("is-active"));
  if (active) active.className = "is-failed";
}

async function responseErrorCode(response, fallback) {
  try { return (await response.json())?.detail?.code ?? fallback; }
  catch { return fallback; }
}
function errorCode(error, fallback) { return error instanceof Error && error.message ? error.message : fallback; }

async function downloadPackage() {
  if (!state.job) return;
  elements.download.disabled = true;
  try {
    const response = await timedFetch(state.job.download, {}, 120000);
    if (!response.ok) throw new Error(await responseErrorCode(response, "download_failed"));
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = safeDownloadName(response.headers.get("content-disposition"), `${state.job.id}.vr3d`);
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) { failJob(errorCode(error, "download_failed")); }
  finally { elements.download.disabled = false; }
}

elements.input.addEventListener("change", () => { if (elements.input.files?.[0]) selectFile(elements.input.files[0]); });
elements.removeFile.addEventListener("click", clearSelectedFile);
elements.form.addEventListener("submit", submitJob);
elements.download.addEventListener("click", downloadPackage);
elements.language.addEventListener("click", () => { state.language = state.language === "ko" ? "en" : "ko"; applyLanguage(); });
["dragenter", "dragover"].forEach((name) => elements.dropZone.addEventListener(name, (event) => { event.preventDefault(); elements.dropZone.classList.add("is-dragging"); }));
["dragleave", "drop"].forEach((name) => elements.dropZone.addEventListener(name, (event) => { event.preventDefault(); elements.dropZone.classList.remove("is-dragging"); }));
elements.dropZone.addEventListener("drop", (event) => { if (event.dataTransfer?.files?.[0]) selectFile(event.dataTransfer.files[0]); });
window.addEventListener("beforeunload", () => { window.clearTimeout(state.pollTimer); if (state.previewUrl) URL.revokeObjectURL(state.previewUrl); });

applyLanguage();
discoverEndpoint();
window.setInterval(discoverEndpoint, 30000);

export { discoverEndpoint, selectFile };
