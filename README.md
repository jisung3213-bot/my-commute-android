# 나만의 출퇴근 (MyCommuteAndroid)

자주 가는 출근길·퇴근길을 저장하고, 목표 도착 시각과 날씨, 현재 위치, 대중교통 정보를 바탕으로 이동을 안내하는 Android 앱입니다.

## 실행

1. Android Studio에서 `settings.gradle`이 있는 이 폴더를 엽니다. Gradle JDK 17을 선택한 뒤 Sync합니다.
2. Firebase의 Android 앱 설정을 `app/google-services.json`에 두고 이메일·비밀번호 인증을 켭니다. [Firebase와 API 설정](docs/API_SETUP.md)
3. `local.properties`의 기존 `sdk.dir`을 유지하면서 필요한 서비스 키를 설정합니다. TMAP 지도는 공급사 Raster SDK를 `app/libs/`에 설치하면 사용할 수 있습니다. SDK 초기화가 불가능하면 OpenStreetMap 지도를 시도합니다. [설정 예시](local.properties.example) · [TMAP SDK](https://tmapapi.tmapmobility.com/main.html#android/sample/androidSample.sdk_download)
4. `app`을 실행하고 로그인합니다. **루틴**에서 출근길과 퇴근길의 장소·목표 도착 시각·반복 요일을 입력하고, **경로 검색**에서 후보를 골라 각 방향의 기본 경로로 저장합니다. [처음 사용하는 방법](docs/USER_GUIDE.md)

앱 0.2.0(빌드 번호 2), 앱 ID `com.example.mycommute`. Java 17과 XML, Android Studio 2023.1.1 계열 개발 환경, AGP 8.2.0, Gradle 8.2를 사용합니다. Android SDK 설정은 `compileSdk 34`, `minSdk 26`, `targetSdk 28`입니다.

## 주요 동작

- 홈은 반복 일정에서 가장 가까운 다음 목표 도착 방향을 표시합니다. 출근길·퇴근길 탭으로 전환하고 홈에서 알림 로그를 열 수 있습니다.
- 루틴은 경유지를 최대 두 곳까지 지원하며, 도착일 기준 반복 요일을 한 줄에서 선택합니다. 강수와 기온 예보가 있으면 도보 부담을 경로 추천 순위에 반영합니다.
- 선택한 경로는 루틴에 저장합니다. 상세 경로는 기기에 최대 23시간 보관하며, 유효한 상세 정보가 필요하면 TMAP에서 같은 경로를 다시 찾습니다.
- 자동 추적을 켜면 기본 출발지 500m 이내 위치가 예정 출발 30분 전부터 출발 시각 사이에 확인된 회차만 자동 안내와 예약 알림을 시작합니다. 수동 안내도 최신 위치로 출발지 반경을 확인합니다.
- 출발지 범위 밖에서 실시간 이동 화면을 열면 출근·퇴근 방향에 맞는 위치 안내가 표시됩니다. 설정의 **출퇴근 위치 안내 표시**를 끄면 시연 중 문구를 숨길 수 있습니다. 위치 조건은 계속 적용됩니다. 시간 표기는 12시간제와 24시간제 중 선택할 수 있습니다.

## 검증과 문서

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug assembleRelease lintDebug
.\scripts\test-core.ps1
```

Windows 한글 경로에서 JUnit 전체를 실행할 때는 `scripts/test-core.ps1`을 사용합니다. 로컬 파일럿의 `targetSdk 28`을 유지하기 위해 `app/lint.xml`에서 `app/build.gradle`의 `ExpiredTargetSdkVersion` 검사만 예외로 둡니다.

- [문서 안내](docs/README.md) · [화면별 사용 방법](docs/USER_GUIDE.md)
- [과제 기술 요구사항](docs/REQUIREMENTS_CURRENT.md) · [실행 검증 결과](docs/STATUS.md) · [시연·UI 점검표](docs/UI_LAYOUT_QA.md)
- [현재 코드와 데이터 흐름](docs/ARCHITECTURE_CURRENT.md) · [알림 상세](docs/ALARM_FEATURES.md)
- [개발 이력과 이전 검증 자료](docs/history/) · [반입 원본 설명서](docs/imported/)

## 전체 파일 구조와 GitHub 반영 상태

이번 업데이트의 파일 목록을 직전 GitHub `main` 기준인 `31a5b22`와 비교했습니다.

- **(업데이트됨)** 이번 업데이트에서 수정·추가·이동된 파일
- **(올라가 있음)** 비교 기준과 내용이 같은 Git 관리 파일
- **(올라가지 않음)** 로컬 설정, 공급사 SDK, 빌드 결과 등 Git 제외 항목

이전 경로의 `docs/API_INTEGRATION_VERIFIED.md`, `docs/DEVELOPMENT_HISTORY.md`, `docs/HANDOFF.md`는 `docs/history/`로 옮겼습니다. 기존 상태·알림 실험·화면 점검 기록도 날짜를 붙여 이력으로 옮겼으며, 원래 경로에는 현재 안내가 있습니다.

<details>
<summary>프로젝트 전체 파일 펼치기</summary>

```text
MyCommuteAndroid/
├── .git/ (올라가지 않음) — Git 저장소 메타데이터
├── .gitattributes (올라가 있음)
├── .gitignore (올라가 있음)
├── .gradle/ (올라가지 않음) — 재생성되는 Gradle 캐시
├── .idea/ (올라가지 않음) — 로컬 Android Studio 프로젝트 설정
├── app/
│   ├── build.gradle (올라가 있음)
│   ├── build/ (올라가지 않음) — APK와 자동 생성 소스, 테스트·Lint 보고서
│   ├── google-services.json (올라가지 않음) — 환경별 Firebase 연결 설정
│   ├── libs/
│   │   └── com.skt.Tmap_1.77.jar (올라가지 않음) — 별도로 설치하는 공급사 SDK
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
│       │   │               │   ├── NotificationHelper.java (업데이트됨)
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
│       │   │               │   ├── RoutineStore.java (업데이트됨)
│       │   │               │   ├── Session.java (올라가 있음)
│       │   │               │   ├── TransitRouteCache.java (올라가 있음)
│       │   │               │   └── TransitRouteMemory.java (올라가 있음)
│       │   │               ├── engine/
│       │   │               │   ├── AlertThresholds.java (올라가 있음)
│       │   │               │   ├── BoardingFeasibilityEngine.java (올라가 있음)
│       │   │               │   ├── CommuteWindow.java (업데이트됨)
│       │   │               │   ├── LiveDepartureAdvisor.java (올라가 있음)
│       │   │               │   ├── PlanSchedule.java (올라가 있음)
│       │   │               │   ├── RoutePlanner.java (올라가 있음)
│       │   │               │   ├── RouteSignature.java (올라가 있음)
│       │   │               │   ├── StagePositionMatcher.java (올라가 있음)
│       │   │               │   └── WeatherRouteRanker.java (올라가 있음)
│       │   │               ├── location/
│       │   │               │   └── TrackingService.java (업데이트됨)
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
│       │   │                   ├── SettingsActivity.java (업데이트됨)
│       │   │                   ├── TmapRasterMapAdapter.java (올라가 있음)
│       │   │                   ├── TrackingActivity.java (업데이트됨)
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
│       │       │   ├── activity_custom.xml (올라가 있음)
│       │       │   ├── activity_login.xml (올라가 있음)
│       │       │   ├── activity_main.xml (올라가 있음)
│       │       │   ├── activity_map.xml (올라가 있음)
│       │       │   ├── activity_place_search.xml (올라가 있음)
│       │       │   ├── activity_settings.xml (업데이트됨)
│       │       │   ├── activity_tracking.xml (업데이트됨)
│       │       │   ├── item_route_result.xml (올라가 있음)
│       │       │   └── view_bottom_nav.xml (올라가 있음)
│       │       ├── values/
│       │       │   ├── colors.xml (올라가 있음)
│       │       │   ├── strings.xml (올라가 있음)
│       │       │   └── themes.xml (올라가 있음)
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
│                           │   ├── CommuteWindowTest.java (업데이트됨)
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
│   ├── ALARM_FEATURES.md (업데이트됨)
│   ├── ALERT_LAB.md (업데이트됨)
│   ├── API_SETUP.md (업데이트됨) — 새 문서
│   ├── ARCHITECTURE_CURRENT.md (업데이트됨)
│   ├── history/
│   │   ├── ALERT_LAB_2026-11-20.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   │   ├── API_INTEGRATION_VERIFIED.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   │   ├── DEVELOPMENT_HISTORY.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   │   ├── HANDOFF.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   │   ├── README.md (업데이트됨) — 새 문서
│   │   ├── STATUS_2026-11-19.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   │   └── UI_LAYOUT_2026-11-19.md (업데이트됨) — 이전 문서에서 이동 또는 보관
│   ├── imported/
│   │   ├── API_SETUP.md (올라가 있음)
│   │   ├── ARCHITECTURE.md (올라가 있음)
│   │   ├── FILE_MANIFEST.json (올라가 있음)
│   │   ├── README_ORIGINAL.md (올라가 있음)
│   │   ├── README.md (업데이트됨) — 새 문서
│   │   ├── TEST_CASES.md (올라가 있음)
│   │   └── VALIDATION.md (올라가 있음)
│   ├── NEXT_STEPS.md (업데이트됨)
│   ├── README.md (업데이트됨) — 새 문서
│   ├── REQUIREMENTS_CURRENT.md (업데이트됨)
│   ├── STATUS.md (업데이트됨)
│   ├── UI_LAYOUT_QA.md (업데이트됨)
│   └── USER_GUIDE.md (업데이트됨) — 새 문서
├── firestore.rules (올라가 있음)
├── gradle.properties (올라가 있음)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar (올라가 있음)
│       └── gradle-wrapper.properties (올라가 있음)
├── gradlew (올라가 있음)
├── gradlew.bat (올라가 있음)
├── local.properties (올라가지 않음) — 기기별 SDK 경로와 서비스 키 설정
├── local.properties.example (올라가 있음)
├── README.md (업데이트됨)
├── scripts/
│   └── test-core.ps1 (올라가 있음)
└── settings.gradle (올라가 있음)
```

</details>

상위 작업 공간의 `AGENTS.md`, `WORKSPACE.md`, `agent-notes/`, `materials/`는 Android 저장소 밖에서 개발 기록·입력 원본·검증 캡처를 관리합니다.
