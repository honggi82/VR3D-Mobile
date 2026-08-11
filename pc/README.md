# VR3D Mobile PC service

Run `run_admin.bat` from Windows. The service intentionally remains closed to uploads until
all three mandatory gates report healthy: AhnLab V3 real-time processes, an explicit local
file scanner, and an explicit content-safety scanner.

Configuration is through environment variables. Commands are JSON argument arrays with
exactly one `{path}` placeholder; they are executed without a shell. A zero exit code is the
only clean/safe result. Use absolute executable paths.

```powershell
$env:VR3D_SCANNER_COMMAND='["C:\\absolute\\scanner.exe","--scan","{path}"]'
$env:VR3D_CONTENT_COMMAND='["C:\\absolute\\content-check.exe","{path}"]'
$env:VR3D_CLOUDFLARED='C:\absolute\cloudflared.exe'
pc\run_admin.bat
```

The GUI tests both scanner commands against a local safe canary before `publicReady` can be
true. On a Quick Tunnel URL, it also verifies local API health before atomically setting
`web/endpoint.json` online. Stopping the GUI writes an offline endpoint. It never commits or
pushes that file; GitHub publication remains a separately approved operation.

The API runs under the normal Python environment. If that environment lacks the model's
dependencies, depth inference automatically uses the untouched existing runtime at
`C:\Users\user\Documents\VR3D_v1_runtime\venv\Scripts\python.exe` in an isolated child
process. Inputs and results are stored below `pc/data` and removed after 24 hours by the
server cleanup loop.

Verification:

```powershell
cd pc
python -m compileall -q vr3d_pc tests
python -m pytest -q
```
