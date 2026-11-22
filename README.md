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
4. Android Studio에서 `app`을 실행한 뒤 로그인합니다. **루틴** 탭에서 출발지·도착지·경유지, 목표 도착 시각과 반복 요일을 입력하고 **경로 검색**을 누릅니다. 원하는 결과를 선택하고 방향별 기본 경로를 지정합니다.

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
- 경유지는 최대 2개이며 경유지 사이의 경로를 합성합니다. 기상과 도착 여유를 반영한 추천 경로 1개를 먼저 표시합니다. 저장은 사용자가 확정합니다. 루틴의 반복 요일은 월요일부터 일요일까지 한 줄에서 선택합니다.
- 홈에는 다음 목표 도착 일정의 방향이 표시됩니다. 출근길·퇴근길을 직접 전환하거나 홈에서 알림 로그를 열 수 있습니다. 시간 표기는 설정에서 24시간제와 12시간제(오전/오후) 중 선택합니다.
- 선택한 경로의 상세 정보는 기기에 최대 23시간 보관합니다. 유효한 정보가 필요할 때 TMAP을 다시 조회해 저장한 선택값과 일치하는 경로를 갱신합니다.
- **출퇴근 자동 위치 추적**을 켜면 예정 출발 30분 전부터 출발 시각까지 출발지 반경 500m 이내에서 위치를 확인한 회차에 자동 안내와 알림을 시작합니다. 앱 화면이 닫혀 있을 때도 확인하려면 기기의 백그라운드 위치 권한을 허용합니다.
- 이동 화면에 예보, GPS 구간 약도, 유효한 차량 도착시간과 탑승 여유를 표시합니다. 위치 추적은 이동 화면에서 수동으로 시작할 수도 있습니다.
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

## 파일 구조와 GitHub 반영 상태

로컬 Android 프로젝트와 GitHub `main`의 비교 기준은 `6c404b3`입니다. 아래에 Git이 관리하는 전체 파일을 표시했습니다. 로컬 빌드 결과와 캐시는 폴더 단위로 표시합니다.

- **(업데이트됨)** 이번 커밋에서 변경한 파일
- **(올라가 있음)** 비교 기준과 내용이 같은 GitHub 파일
- **(올라가지 않음)** 로컬에만 있는 파일 또는 폴더. 오른쪽에 이유를 표시했습니다.

<details>
<summary>프로젝트 파일 전체 펼치기</summary>

```text
MyCommuteAndroid/
├── .git/ (올라가지 않음) — 로컬 Git 저장소 메타데이터
├── .gitattributes (올라가 있음)
├── .gitignore (올라가 있음)
├── .gradle/ (올라가지 않음) — 실행하면 재생성되는 Gradle 캐시
├── .idea/ (올라가지 않음) — Android Studio의 로컬 프로젝트·사용자 설정
├── app/
│   ├── build/ (올라가지 않음) — APK, 자동 생성 소스, 테스트·Lint 보고서
│   ├── build.gradle (올라가 있음)
│   ├── google-services.json (올라가지 않음) — Firebase 프로젝트별 연결 설정
│   ├── libs/ (올라가지 않음) — TMAP 공급사 SDK를 별도로 설치하는 폴더
│   │   └── com.skt.Tmap_1.77.jar (올라가지 않음) — 공급사에서 배포하는 SDK 바이너리
│   ├── lint.xml (올라가 있음)
│   └── src/
│       ├── debug/
│       │   ├── AndroidManifest.xml (올라가 있음)
│       │   └── java/
│       │       └── com/
│       │           └── example/
│       │               └── mycommute/
│       │                   └── debug/
│       │                       ├── AlertLabActivity.java (올라가 있음)
│       │                       ├── AlertLabSuite.java (올라가 있음)
│       │                       ├── ApiProbe.java (올라가 있음)
│       │                       ├── RouteGpsLabActivity.java (올라가 있음)
│       │                       ├── RouteRecommendationLabActivity.java (올라가 있음)
│       │                       └── SubwayEtaSurvey.java (올라가 있음)
│       ├── main/
│       │   ├── AndroidManifest.xml (올라가 있음)
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── example/
│       │   │           └── mycommute/
│       │   │               ├── alarms/
│       │   │               │   ├── AlertCopy.java (올라가 있음)
│       │   │               │   ├── AlertLabMode.java (올라가 있음)
│       │   │               │   ├── AlertLedger.java (올라가 있음)
│       │   │               │   ├── AutoCommute.java (올라가 있음)
│       │   │               │   ├── BootReceiver.java (올라가 있음)
│       │   │               │   ├── NotificationHelper.java (올라가 있음)
│       │   │               │   ├── ReminderReceiver.java (올라가 있음)
│       │   │               │   ├── ReminderScheduler.java (올라가 있음)
│       │   │               │   └── TravelAlertMonitor.java (올라가 있음)
│       │   │               ├── CommuteApplication.java (올라가 있음)
│       │   │               ├── data/
│       │   │               │   ├── AlarmLogDao.java (올라가 있음)
│       │   │               │   ├── AlarmLogEntity.java (올라가 있음)
│       │   │               │   ├── AlarmLogRepository.java (올라가 있음)
│       │   │               │   ├── AlarmSettingsStore.java (올라가 있음)
│       │   │               │   ├── ClockPreference.java (올라가 있음)
│       │   │               │   ├── CommuteDatabase.java (올라가 있음)
│       │   │               │   ├── FirestoreSync.java (올라가 있음)
│       │   │               │   ├── PinnedRouteRefresh.java (올라가 있음)
│       │   │               │   ├── RouteRefreshJob.java (올라가 있음)
│       │   │               │   ├── RoutineStore.java (올라가 있음)
│       │   │               │   ├── Session.java (올라가 있음)
│       │   │               │   ├── TransitRouteCache.java (올라가 있음)
│       │   │               │   └── TransitRouteMemory.java (올라가 있음)
│       │   │               ├── engine/
│       │   │               │   ├── AlertThresholds.java (올라가 있음)
│       │   │               │   ├── BoardingFeasibilityEngine.java (올라가 있음)
│       │   │               │   ├── CommuteWindow.java (올라가 있음)
│       │   │               │   ├── LiveDepartureAdvisor.java (올라가 있음)
│       │   │               │   ├── PlanSchedule.java (올라가 있음)
│       │   │               │   ├── RoutePlanner.java (올라가 있음)
│       │   │               │   ├── RouteSignature.java (올라가 있음)
│       │   │               │   ├── StagePositionMatcher.java (올라가 있음)
│       │   │               │   └── WeatherRouteRanker.java (올라가 있음)
│       │   │               ├── location/
│       │   │               │   └── TrackingService.java (올라가 있음)
│       │   │               ├── model/
│       │   │               │   ├── Place.java (올라가 있음)
│       │   │               │   ├── Routine.java (올라가 있음)
│       │   │               │   ├── TransitArrival.java (올라가 있음)
│       │   │               │   ├── TransitRoute.java (올라가 있음)
│       │   │               │   └── Waypoint.java (올라가 있음)
│       │   │               ├── network/
│       │   │               │   ├── ArrivalApiError.java (올라가 있음)
│       │   │               │   ├── ArrivalMatcher.java (올라가 있음)
│       │   │               │   ├── KmaWeatherClient.java (올라가 있음)
│       │   │               │   ├── LiveArrivalClient.java (올라가 있음)
│       │   │               │   ├── SeoulBusClient.java (올라가 있음)
│       │   │               │   ├── SeoulXmlParser.java (올라가 있음)
│       │   │               │   ├── TmapPoiClient.java (올라가 있음)
│       │   │               │   ├── TmapTransitClient.java (올라가 있음)
│       │   │               │   ├── TmapWalkingClient.java (올라가 있음)
│       │   │               │   └── TmapWaypointRouteClient.java (올라가 있음)
│       │   │               └── ui/
│       │   │                   ├── AlarmLogActivity.java (올라가 있음)
│       │   │                   ├── CommuteLiveCoordinator.java (올라가 있음)
│       │   │                   ├── CustomActivity.java (올라가 있음)
│       │   │                   ├── LoginActivity.java (올라가 있음)
│       │   │                   ├── MainActivity.java (올라가 있음)
│       │   │                   ├── MapActivity.java (올라가 있음)
│       │   │                   ├── Navigation.java (올라가 있음)
│       │   │                   ├── PlaceSearchActivity.java (올라가 있음)
│       │   │                   ├── RouteSchematicLabels.java (올라가 있음)
│       │   │                   ├── RouteSchematicView.java (올라가 있음)
│       │   │                   ├── SettingsActivity.java (올라가 있음)
│       │   │                   ├── TmapRasterMapAdapter.java (올라가 있음)
│       │   │                   ├── TrackingActivity.java (올라가 있음)
│       │   │                   └── UiNotice.java (올라가 있음)
│       │   └── res/
│       │       ├── color/
│       │       │   ├── weekday_fill.xml (올라가 있음)
│       │       │   └── weekday_text.xml (올라가 있음)
│       │       ├── drawable/
│       │       │   ├── bg_card.xml (올라가 있음)
│       │       │   ├── bg_route_selected.xml (올라가 있음)
│       │       │   └── ic_notification.xml (올라가 있음)
│       │       ├── layout/
│       │       │   ├── activity_alarm_log.xml (올라가 있음)
│       │       │   ├── activity_custom.xml (업데이트됨)
│       │       │   ├── activity_login.xml (올라가 있음)
│       │       │   ├── activity_main.xml (올라가 있음)
│       │       │   ├── activity_map.xml (올라가 있음)
│       │       │   ├── activity_place_search.xml (올라가 있음)
│       │       │   ├── activity_settings.xml (올라가 있음)
│       │       │   ├── activity_tracking.xml (올라가 있음)
│       │       │   ├── item_route_result.xml (올라가 있음)
│       │       │   └── view_bottom_nav.xml (올라가 있음)
│       │       ├── values/
│       │       │   ├── colors.xml (올라가 있음)
│       │       │   ├── strings.xml (올라가 있음)
│       │       │   └── themes.xml (업데이트됨)
│       │       └── xml/
│       │           └── network_security_config.xml (올라가 있음)
│       └── test/
│           └── java/
│               └── com/
│                   └── example/
│                       └── mycommute/
│                           ├── engine/
│                           │   ├── AlertThresholdsTest.java (올라가 있음)
│                           │   ├── BoardingFeasibilityEngineTest.java (올라가 있음)
│                           │   ├── CommuteWindowTest.java (올라가 있음)
│                           │   ├── LiveDepartureAdvisorTest.java (올라가 있음)
│                           │   ├── PlanScheduleTest.java (올라가 있음)
│                           │   ├── RouteSignatureTest.java (올라가 있음)
│                           │   ├── StagePositionMatcherTest.java (올라가 있음)
│                           │   └── WeatherRouteRankerTest.java (올라가 있음)
│                           ├── model/
│                           │   ├── NamedTransitCacheTest.java (올라가 있음)
│                           │   ├── PlaceTest.java (올라가 있음)
│                           │   ├── TransitArrivalFollowUpTest.java (올라가 있음)
│                           │   ├── TransitArrivalTest.java (올라가 있음)
│                           │   └── TransitRouteTest.java (올라가 있음)
│                           ├── network/
│                           │   ├── ArrivalApiErrorTest.java (올라가 있음)
│                           │   ├── ArrivalMatcherTest.java (올라가 있음)
│                           │   ├── KmaWeatherGridTest.java (올라가 있음)
│                           │   ├── SubwayArrivalStatusTest.java (올라가 있음)
│                           │   └── SubwayUnverifiedDirectionTest.java (올라가 있음)
│                           └── ui/
│                               └── RouteSchematicLabelsTest.java (올라가 있음)
├── build.gradle (올라가 있음)
├── docs/
│   ├── ALARM_FEATURES.md (올라가 있음)
│   ├── ALERT_LAB.md (올라가 있음)
│   ├── API_INTEGRATION_VERIFIED.md (올라가 있음)
│   ├── ARCHITECTURE_CURRENT.md (올라가 있음)
│   ├── DEVELOPMENT_HISTORY.md (올라가 있음)
│   ├── HANDOFF.md (올라가 있음)
│   ├── imported/
│   │   ├── API_SETUP.md (올라가 있음)
│   │   ├── ARCHITECTURE.md (올라가 있음)
│   │   ├── FILE_MANIFEST.json (올라가 있음)
│   │   ├── README_ORIGINAL.md (올라가 있음)
│   │   ├── TEST_CASES.md (올라가 있음)
│   │   └── VALIDATION.md (올라가 있음)
│   ├── NEXT_STEPS.md (올라가 있음)
│   ├── REQUIREMENTS_CURRENT.md (올라가 있음)
│   ├── STATUS.md (올라가 있음)
│   └── UI_LAYOUT_QA.md (올라가 있음)
├── firestore.rules (올라가 있음)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar (올라가 있음)
│       └── gradle-wrapper.properties (올라가 있음)
├── gradle.properties (올라가 있음)
├── gradlew (올라가 있음)
├── gradlew.bat (올라가 있음)
├── local.properties (올라가지 않음) — 기기별 SDK 경로와 API 키 설정
├── local.properties.example (올라가 있음)
├── README.md (업데이트됨)
├── scripts/
│   └── test-core.ps1 (올라가 있음)
└── settings.gradle (올라가 있음)
```

</details>

프로젝트 바깥의 작업 공간에는 아래 파일과 폴더가 있습니다.

| 경로 | 상태 및 이유 |
| --- | --- |
| `AGENTS.md`, `WORKSPACE.md` | (올라가지 않음) — 상위 작업 공간의 개발 안내 |
| `agent-notes/` | (올라가지 않음) — 로컬 진행·검증 기록 |
| `materials/` | (올라가지 않음) — 상위 작업 공간에 보관하는 참고 원본, 화면 캡처와 생성 자료 |
