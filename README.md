# 나만의 출퇴근 (MyCommuteAndroid)

출근·퇴근별 루틴을 등록하고, 대중교통 경로·날씨·현재 위치를 이용해 출발과 이동을 안내하는 Android 앱입니다.

## 개발 환경

- Java 17 + XML, AGP 8.2.0, Gradle Wrapper 8.2
- `compileSdk 34`, `minSdk 26`, `targetSdk 28`
- 앱 ID `com.example.mycommute`. 이전 API 테스트 앱(`com.example.seoulmobilitytester`)과 저장 데이터가 별도입니다.

## 실행

1. Android Studio에서 `settings.gradle`이 있는 이 폴더를 열고 Gradle JDK 17로 Sync합니다.
2. Firebase Console에 Android 앱 `com.example.mycommute`를 등록하고 `app/google-services.json`을 둡니다. 이메일/비밀번호 인증을 켜고 필요한 경우 `firestore.rules`를 Firebase에 적용합니다. 데모 로그인은 제공하지 않습니다.
3. `local.properties`에 필요한 API 키를 설정하고 다시 Sync합니다. 기존 `sdk.dir`은 유지합니다. 키 이름은 `local.properties.example`과 아래 표를 확인하세요.
4. Android Studio에서 `app`을 실행한 뒤 로그인합니다. 커스텀 화면에서 출발·도착·경유지, 목표시간·요일을 입력하고 **대중교통 경로 검색**을 누릅니다. 원하는 결과를 선택한 뒤 저장합니다.

| 기능 | 설정 |
| --- | --- |
| TMAP 장소 검색·도보·지도 | `TMAP_APP_KEY` |
| TMAP 대중교통 경로 검색 | `TMAP_TRANSIT_APP_KEY` |
| 기상청 단기예보 | `KMA_SERVICE_KEY` |
| 서울 지하철 | `SEOUL_SUBWAY_API_KEY` |
| 서울 버스 / 그 외 TAGO 버스 | `SEOUL_BUS_SERVICE_KEY` / `TAGO_BUS_SERVICE_KEY` |

공공데이터포털 서비스에는 `PUBLIC_DATA_SERVICE_KEY`를 기본값으로 쓸 수 있습니다. 개별 설정이 우선하고 서비스별 활용 승인은 따로 필요할 수 있습니다. 키와 Firebase 설정 파일은 Git에서 제외되지만 앱의 REST API 키는 개발용 APK에 포함됩니다.

지도에 TMAP을 사용하려면 [공식 Raster SDK](https://tmapapi.tmapmobility.com/main.html#android/sample/androidSample.sdk_download)의 `.jar` 또는 `.aar`를 `app/libs/`에 둡니다. 지도는 SDK가 없거나 초기화가 실패하면 OSM을 사용합니다.

## 현재 동작

- 복수의 출근·퇴근 루틴을 계정별로 저장하고 Firebase로 동기화합니다.
- 경유지는 최대 2개이며 경유지 사이의 경로를 합성합니다. 기상과 도착 여유를 반영한 추천 경로 1개를 먼저 표시합니다. 저장은 사용자가 확정합니다.
- 선택 후보의 상세 경로는 기기에 최대 23시간 보관합니다. 이동 화면에서 저장 경로를 표시할 때 TMAP 대중교통 경로에 새 요청을 보내지 않습니다.
- 이동 화면에 예보, GPS 구간 약도, 유효한 차량 도착시간과 탑승 여유를 표시합니다. 이동 중 알림은 사용자가 시작한 위치 추적 서비스에서 평가합니다.
- 예정된 출발과 이동 중 상황의 알림 10종은 설정에서 조절합니다. 공식 기상특보, 전 구간 실시간 도착 추정, 식사·커피 기능은 후속 범위입니다.

## 확인 명령

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug lintDebug
.\scripts\test-core.ps1
```

`scripts/test-core.ps1`은 Windows 한글 경로에서도 컴파일된 JUnit 테스트 전체를 실행합니다. Gradle 테스트 실행에 문제가 없는 경로에서는 `testDebugUnitTest`를 직접 실행할 수 있습니다.

로컬 파일럿의 `targetSdk 28`을 유지하기 위해 `app/lint.xml`에서 `app/build.gradle`에 해당하는 `ExpiredTargetSdkVersion` 검사만 예외로 처리합니다. 이 설정은 Google Play 배포 요건 충족을 뜻하지 않습니다.

## 문서

- [현재 구조와 기능](docs/ARCHITECTURE_CURRENT.md)
- [개발 이력과 브랜치](docs/DEVELOPMENT_HISTORY.md)
- [알림 조건과 설정](docs/ALARM_FEATURES.md) · [디버그 실험실](docs/ALERT_LAB.md)
- [이전 기기의 실제 API 진단](docs/API_INTEGRATION_VERIFIED.md) · [화면 점검표](docs/UI_LAYOUT_QA.md)

`docs/STATUS.md`, `docs/HANDOFF.md`, `docs/imported/`는 작성 시점의 구현 또는 검증 기록입니다.
