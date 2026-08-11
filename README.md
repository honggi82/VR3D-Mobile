# VR3D Mobile

한 장의 사진을 PC에서 Video-Depth-Anything으로 처리해 35개 시점의 `.vr3d`
패키지로 만들고, Android 10 이상 휴대폰의 기울기에 맞춰 주변 시점을 부드럽게
혼합해 보여주는 전체 구현입니다. 기존 `VR3D_v1`과 그 모델 파일은 수정하지 않습니다.

## 구성

- `pc/`: FastAPI 처리 서버와 Tkinter 운영 GUI
- `web/`: GitHub Pages용 한국어/영어 업로드 페이지
- `android/`: Android Studio용 Kotlin 프로젝트
- `contracts/`: API v1 및 `.vr3d` package v1 계약
- `dist/VR3D-Mobile-debug.apk`: Android 10+ 디버그 APK
- `docs/VERIFICATION.md`: 이번 환경에서 실행한 검증과 남은 제한

## 안전 상태

공개 업로드는 기본적으로 **비활성**입니다. `web/endpoint.json`은 offline으로
추적되며 PC API도 다음 세 게이트가 모두 실제 canary를 통과해야만
`publicReady: true`를 반환합니다.

1. AhnLab V3 실시간 보호 프로세스 상태
2. 운영자가 지정한 명시적 로컬 파일 스캐너
3. 운영자가 지정한 콘텐츠 안전 검사기

현재 PC에서는 V3만 확인됐고 나머지 두 검사기가 구성되지 않았으므로 공개 업로드는
열리지 않습니다. 차단 상태를 우회하는 옵션은 없습니다. 입력과 결과는 생성 시각부터
24시간 뒤 정리되며 차단된 격리 파일은 즉시 삭제됩니다.

## PC 실행

Python 3.10+ 환경에 `pc/pyproject.toml`의 의존성을 설치한 뒤 `pc/run_admin.bat`을
실행합니다. 기존 VDA 런타임의 기본 위치는
`C:\Users\user\Documents\VR3D_v1_runtime`이며 `VR3D_RUNTIME_DIR`로 바꿀 수 있습니다.
스캐너와 콘텐츠 검사기는 shell을 거치지 않는 JSON argv 배열로 설정합니다.

```powershell
$env:VR3D_SCANNER_COMMAND='["C:\\absolute\\scanner.exe","--scan","{path}"]'
$env:VR3D_CONTENT_COMMAND='["C:\\absolute\\content-check.exe","{path}"]'
$env:VR3D_CLOUDFLARED='C:\absolute\cloudflared.exe'
pc\run_admin.bat
```

Quick Tunnel URL은 GUI가 감지하지만, 로컬 health가 세 게이트 통과를 보고할 때만
`web/endpoint.json`을 online으로 바꿉니다. GitHub commit/push는 아직 수행하지 않으며
별도 승인과 인증이 필요합니다. `.github/workflows/pages.yml`은 승인 후 `main`에 push하면
`web/`만 GitHub Pages에 배포하도록 준비되어 있습니다.

## Android 빌드

Android Studio에서 `android/`를 열고 JDK 17, SDK 36을 선택합니다. 또는 ASCII 경로에서:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Windows의 Android Gradle Plugin은 한글 경로에서 JVM test classpath 문제가 있으므로,
필요하면 프로젝트를 ASCII 경로로 복사하거나 임시 `subst` 경로에서 빌드합니다.

## 웹 테스트

```powershell
node --test web\test\core.test.mjs
```

웹은 JPG/JPEG, PNG, WebP만 선택할 수 있고 크기·확장자·MIME·파일 서명을 먼저
확인합니다. 이 검사는 사용자 피드백용이며 최종 보안 경계는 항상 PC 서버입니다.
