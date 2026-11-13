# 나만의 출퇴근 (Android)

서울 지역 출퇴근·통학 루틴 안내 앱의 개발 기준 프로젝트입니다.
현재는 기존 **따릉이 / TAGO / TMAP REST API 테스트 앱**이며, 지도·로그인·루틴·알림은 아직 구현하지 않았습니다.

## Android Studio에서 열기

1. **File → Open**에서 이 README와 `settings.gradle`이 있는 **폴더 전체**를 선택합니다. `app` 폴더나 Java 파일 하나만 열지 않습니다.
2. Gradle JDK를 **17**로 지정합니다. 이 PC에서는 Android Studio 내장 `jbr`을 사용할 수 있습니다.
3. Android SDK Platform 34와 Build Tools 34.0.0을 설치하고 Gradle Sync를 실행합니다.
4. 실행 구성 `app`을 선택하고 API 26 이상 기기/에뮬레이터에서 Run 합니다.

이 PC의 작업 폴더: `C:\Users\jisun\Documents\ChatGPT\모바일 프로그래밍`

`local.properties`에는 이 PC의 SDK 경로가 설정되어 있으며 Git에는 포함하지 않습니다.
다른 PC에서는 Android Studio가 SDK 위치를 설정하도록 합니다.
Windows 한글 경로 사용을 위해 `android.overridePathCheck=true`를 설정했습니다.

## 고정된 개발 환경

| 항목 | 값 |
| --- | --- |
| 언어 / UI | Java 17 / XML |
| Android Gradle Plugin | 8.2.0 |
| Gradle Wrapper | 8.2 |
| compileSdk / minSdk | 34 / 26 |
| targetSdk | 28 — 기존 테스트 기준, 배포 준비 단계에서 별도 상향 필요 |
| applicationId | `com.example.seoulmobilitytester` |

PowerShell 빌드:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

현재 APK 빌드는 통과했습니다. `lintDebug`는 원본 targetSdk 28로 인한 오류 1건과 경고 61건이 남아 있습니다.
실기기 실행 및 Android Studio GUI에서의 Sync/Run은 이번 구성 작업에서 확인하지 않았습니다.

## API 키

- 키를 소스나 XML에 넣지 않고 실행한 테스트 화면에서 입력합니다.
- 따릉이 **샘플 5건** 버튼은 개인 키 없이 요청할 수 있습니다.
- TMAP은 사용자가 발급받은 App Key와 해당 API 이용 권한이 필요합니다.
- TAGO는 공식 명세의 요청 URL과 키를 직접 입력하는 범용 테스트 화면입니다.
- `local.properties`, `secrets.properties`, `.env`, `google-services.json`, 서명키, APK 및 빌드 산출물은 Git에서 제외합니다.
- 현재 HTTP 테스트와 낮은 targetSdk 설정을 유지하므로 상용 배포용 앱으로 취급하지 않습니다.

## GitHub와 개발 흐름

비공개 저장소: https://github.com/jisung3213-bot/my-commute-android

`main`은 공유 기준 버전으로 사용하고 기능별 브랜치에서 작업합니다.
파일을 저장하는 것만으로 GitHub가 갱신되지는 않습니다. 커밋 후 푸시해야 합니다.

```powershell
git switch main
git pull --ff-only
git switch -c codex/map-screen
# 코드 수정 후 빌드 확인
.\gradlew.bat assembleDebug
git status
git add <변경한-파일>
git diff --cached
git commit -m "feat: add map screen"
git push -u origin codex/map-screen
```

GitHub에서 Pull Request로 `main`에 반영합니다. 기존 브랜치에서는 `git push`만 실행하면 됩니다.
Android Studio의 Commit / Push 기능으로도 동일하게 작업할 수 있습니다.
새 PC에서는 GitHub 로그인이 필요하며, 비밀번호나 토큰을 저장소 URL에 넣지 않습니다.

## 주요 파일

- `app/src/main/java/com/example/seoulmobilitytester/MainActivity.java`: 따릉이·TAGO 테스트
- `app/src/main/java/com/example/seoulmobilitytester/TmapTestActivity.java`: 장소·도보·대중교통 테스트
- `app/src/main/java/com/example/seoulmobilitytester/TmapApiClient.java`: TMAP HTTP 호출
- `app/src/main/res/layout/`: XML 화면
- `docs/HANDOFF.md`: 전달받은 설계 문서 (목표와 기존 확인 사항)
- `docs/STATUS.md`: 이번 구성 작업의 실제 검증 결과

다음 구현 순서: 기존 테스트 기준 유지 → 네트워크 모듈화 → 지도·따릉이 마커 → 경로 표시 → 로그인·루틴 → 교통·날씨·출발 알림.
