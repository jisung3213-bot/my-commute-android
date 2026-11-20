# 나만의 출퇴근 — 현재 아키텍처·기능·파일 역할

> 기준: 2026-11-20, `feat/isolated-alert-lab`의 코드 및 저장소 문서. 이 문서는 **현재 구현 상태**를 설명하며, 구버전의 누적 개발 기록은 [개발 변천사와 브랜치](DEVELOPMENT_HISTORY.md)를 참고한다. 실제 앱 빌드·실기기 전체 동작을 보증하는 문서가 아니다.

## 1. 목적과 범위

이름 있는 출근/퇴근 경로를 등록하여 목표 도착 시각, 경유지, 보행 구간, 대중교통 도착정보, 날씨와 GPS를 결합하는 Android 통근 보조 앱이다. 고정 루틴 이용자가 필요한 시점에 이동 상태와 알림을 받는 것을 목표로 한다. **자동 운행 제어·승차 확정·전 구간 실시간 내비게이션은 현재 지원하지 않는다.**

- 플랫폼: Java 17, Android XML, Android Studio, AGP 8.2.0/Gradle 8.2.
- SDK 설정: `compileSdk=34`, `minSdk=26`, **`targetSdk=28`**. 최신 Play 배포 요건과는 별개 검토 필요.
- 네임스페이스·패키지: `com.example.mycommute`.
- 주요 의존성: Firebase Auth/Firestore, AndroidX/AppCompat/Material, Fused Location, Room, osmdroid, 선택 설치하는 TMAP Raster SDK.

## 2. 주요 사용자 흐름

```text
Firebase 로그인 / 회원가입
  └─ 메인: 출근·퇴근별 기본 경로 및 상태
     ├─ 커스텀: 이름 있는 루틴, 장소·경유지(최대 2개), 요일·도착시각·여유시간
     │   ├─ TMAP POI 검색 → 장소 좌표 선택
     │   ├─ TMAP 대중교통 경로 검색(명시적으로 호출)
     │   ├─ 기상청 단기예보 반영 → 경로 후보 순위 산정
     │   ├─ 추천 1개 기본 표시 / 다른 후보 접기·펼치기
     │   └─ 경로 선택·저장 → 로컬 캐시 및 루틴 설정
     ├─ 지도: 경로 선형·마커, TMAP Raster SDK 사용 시 지도 / 미설치 시 OSM 대체
     ├─ 실시간 이동: 선택 경로 도식, 예보, 첫 탑승 차량 도착정보, 현재 위치
     │   └─ 명시적으로 GPS 추적 시작 → Foreground Service → 조건부 알림
     ├─ 알림 기록: Room
     └─ 설정: 알림 종류별 기준, 알림 테스트 모드
```

### 화면과 연결 파일

| 기능 | 주요 파일 | 역할 |
|---|---|---|
| 로그인·회원가입 | `ui/LoginActivity.java`, 해당 `res/layout/` | Firebase 인증 기반 진입. 데모 우회 로그인 없음 |
| 메인/루틴 전환 | `ui/MainActivity.java`, `activity_main.xml` | 출근·퇴근 및 기본 경로 선택 |
| 루틴 편집/추천 | `ui/CustomActivity.java`, `activity_custom.xml`, `item_route_result.xml` | 장소·경유지·목표시각·반복요일 입력, 경로 후보 카드 렌더링·선택 |
| 장소 검색 | `ui/PlaceSearchActivity.java`, `network/TmapPoiClient.java` | POI 결과에서 위치 선택 |
| 지도 | `ui/MapActivity.java`, `ui/TmapRasterMapAdapter.java` | 선택 경로 표시; 공급자 SDK 미설치/실패 시 OSM fallback |
| 실시간 이동 | `ui/TrackingActivity.java`, `ui/CommuteLiveCoordinator.java`, `ui/RouteSchematicView.java` | 선택된 경로, 현재/다음 차량, 지도 이동, GPS 마커·상태·날씨 |
| 위치 서비스 | `location/TrackingService.java` | 사용자가 시작하는 포그라운드 위치 업데이트와 이동 중 알림 판단 |
| 설정 | `ui/SettingsActivity.java`, `activity_settings.xml` | 전체/개별 알림 스위치, 기준시간, 테스트 모드 |
| 알림 이력 | `data/AlarmLogRepository.java` 및 Room 관련 클래스 | 발송/차단 이력 저장·조회 |
| 하단 메뉴 | `ui/Navigation.java`, `view_bottom_nav.xml` | 화면 간 이동 |

## 3. 실제 코드의 패키지·파일 역할

아래 트리는 **핵심 파일을 기능별로 선별한 논리적 트리**로, 저장소의 모든 리소스 파일이나 테스트 파일을 빠짐없이 나열한 완전한 `tree` 출력은 아니다.

```text
MyCommuteAndroid/
├─ README.md                    # 실행 설정, 현재 기능 및 문서 링크
├─ settings.gradle              # :app 단일 Android 모듈 선언
├─ build.gradle / gradle.properties / gradle/wrapper/
├─ app/
│  ├─ build.gradle              # SDK, Firebase, 의존성, local.properties → BuildConfig
│  ├─ libs/                     # 개발자가 별도 설치하는 TMAP SDK (Git 미포함)
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml # 권한, Activity/Service/Receiver
│     │  ├─ java/com/example/mycommute/
│     │  │  ├─ ui/              # Activity, 상태 렌더러, 경로 도식, 지도 어댑터
│     │  │  ├─ model/           # Routine, TransitRoute, TransitArrival, Waypoint 등
│     │  │  ├─ data/            # 계정·루틴 로컬/Firestore, 경로 캐시, Room/알림 설정
│     │  │  ├─ network/         # TMAP·서울시·TAGO·기상청 REST 클라이언트와 파서
│     │  │  ├─ engine/          # 경로 점수, 출발·승차·GPS 구간 판정 순수 계산
│     │  │  ├─ alarms/          # 예약 알림, 실시간 조건 평가, 문구·발송·중복방지
│     │  │  └─ location/        # 사용자 시작 Foreground GPS 서비스
│     │  └─ res/
│     │     ├─ layout/          # XML 화면·경로 후보 카드·하단 메뉴
│     │     ├─ drawable/        # 카드/선택 표시 스타일
│     │     ├─ values/          # 색·문자열·스타일
│     │     └─ xml/             # 네트워크 보안 설정
│     ├─ debug/
│     │  ├─ AndroidManifest.xml # release에는 포함되지 않는 테스트 Activity
│     │  └─ java/.../debug/
│     │     ├─ AlertLabActivity.java           # 디버그 메뉴·수동 알림 테스트
│     │     ├─ AlertLabSuite.java              # 10종 모의 알림 일괄 검사
│     │     ├─ ApiProbe.java                   # API 진단
│     │     ├─ SubwayEtaSurvey.java            # 12개 역 원본 ETA 가용성 조사
│     │     ├─ RouteGpsLabActivity.java        # 구간·진행률 마커 시뮬레이터
│     │     └─ RouteRecommendationLabActivity.java # 기상·여유시간 추천 점수 시험
│     └─ test/java/.../         # JUnit 엔진·파서·모델 테스트
├─ docs/                        # 설계·현황·API·알림·테스트 관련 기록
├─ firestore.rules              # 서버에 별도 배포해야 하는 Firestore 규칙
└─ scripts/                     # Windows 전체 JUnit 테스트 실행
```

### 핵심 클래스 책임

| 경로 | 책임과 주의사항 |
|---|---|
| `data/RoutineStore.java` | 계정 단위 SharedPreferences, 출근/퇴근별 복수 루틴, 선택/기본 경로, 기존 슬롯 호환 |
| `data/FirestoreSync.java` | `users/{uid}/routines/{id}`, `users/{uid}/settings/commute`에 사용자 설정 동기화. 공급자 경로 좌표는 업로드하지 않음 |
| `data/TransitRouteCache.java` | 선택 경로의 TMAP 상세 응답을 사용자별 로컬에서 **23시간 TTL**로 캐시, 루틴 장소 변경 시 무효화 |
| `data/TransitRouteMemory.java` | 프로세스 내 선택 후보/경로 관리 |
| `model/TransitRoute.java` | TMAP 대중교통 구간·도보·환승·총시간·좌표/정차역·캐시 직렬화 |
| `model/TransitArrival.java` | 검증된 ETA, 열차 상태, 도착정보 신선도. 상태 정보와 수치 ETA 구분 |
| `network/TmapTransitClient.java`, `TmapWaypointRouteClient.java` | 명시적 대중교통 경로 검색, 경유지마다 구간 분할 후 합성(경유지 0/1/2개 → 검색 1/2/3회) |
| `network/TmapWalkingClient.java` | 현재 GPS → 승차지까지 도보 경로/시간 조회 |
| `network/LiveArrivalClient.java`, `ArrivalMatcher.java`, `SeoulBusClient.java`, `SeoulXmlParser.java` | 서울 지하철·버스 / TAGO 도착정보 호출·파싱, 승차지·노선·방향 교차 검증 |
| `network/KmaWeatherClient.java` | 기상청 단기예보에서 강수형태·확률·기온 확인. **공식 한파주의보/경보 API 미연결** |
| `engine/WeatherRouteRanker.java` | 경로별 선호 점수·추천 순위·최단 대비 추가시간 범위. 실사용 및 테스트 동일 `rank()` 호출 |
| `engine/StagePositionMatcher.java` | GPS 신선도·정확도, 경로 구간 매칭; 모호하면 판정 보류 |
| `engine/CommuteWindow.java`, `PlanSchedule.java` | 목표 도착일·시각, 출발 계획, 시간창 계산 |
| `engine/BoardingFeasibilityEngine.java`, `AlertThresholds.java` | 검증된 위치·ETA·도보시간으로 승차 여유/알림 조건 검사 |
| `alarms/ReminderScheduler.java`, `ReminderReceiver.java` | 출발 준비·출발 권장·날씨 관련 예약 알림(AlarmManager) |
| `alarms/TravelAlertMonitor.java` | GPS 서비스에서 도착 임박·승차지 출발·환승·하차·지연·이탈·종료 평가 |
| `alarms/AlertCopy.java` | 실제 발송과 디버그 시뮬레이터가 **같은 제목/본문 생성식** 사용 |
| `alarms/NotificationHelper.java`, `AlertLedger.java` | 알림 채널·발송, 통근/유형/구간 중복 발송 방지 |
| `alarms/AlertLabMode.java` | 디버그 격리 모드. 활성 중 실제 추적/예약은 중지되고, 모의 알림과 분리됨 |

## 4. 경로 검색·추천의 정확한 동작

1. 사용자가 커스텀에서 장소·경유지·목표 도착시각·여유시간을 저장하고 **경로 검색 버튼**을 눌러야 TMAP 조회가 시작됨.
2. TMAP 후보에서 각 경로의 `durationMinutes`, `walkingMinutes`, `transfers`를 취득함.
3. 기상 적용이 켜져 있고 유효한 기상청 예보를 받았을 때 비·눈 및 기온을 가중치에 반영함. 아직 **공식 한파특보 자체는 반영하지 않음**.
4. `WeatherRouteRanker.rank()`가 최단 소요시간 기준 **허용 범위 내 경로를 먼저 배치**하고, 각 그룹 내 점수가 낮은 순으로 정렬함. 그중 첫 경로가 화면 상단의 추천 1개이며 나머지는 접힌 컨테이너로 표시함.
5. 추천 1위는 자동으로 사용자의 **선택 경로가 되는 것이 아니다**. 경로 선택을 명시적으로 해야 `selected` 및 캐시가 갱신됨.

점수(낮을수록 우선):

```text
score = 총 소요시간(분)
      + 도보 가중치 × 도보시간(분)
      + 환승 가중치 × 환승 횟수
      + 야외 대기 가중치 × 야외 대기시간(분)
```

| 기준 | 설정값 |
|---|---|
| 기본 도보/실외 대기 가중치 | 각각 0.25 |
| 비·눈(강수) | 도보 +1.5, 야외 대기 +1.0 |
| 한파특보 플래그(엔진·모의실험에만 연결) | 도보 +2.0, 야외 대기 +2.5 |
| 영하 기온(한파 플래그가 없는 경우) | 도보 +1.0, 야외 대기 +1.5 |
| 환승 가중치 | 여유 0~7분: 3 / 8~14분: 6 / 15분 이상: 8 |
| 우선 검토 경로의 추가 소요시간 | `min(12, floor(도착 여유시간/2))` 분 |
| 실제 후보의 야외 대기시간 | **현재 0으로 입력**: 독립 산출 미구현 |
| 자전거·킥보드 후보 | 실제 대중교통 후보에 포함되지 않음; 악천후 자전거 배제는 엔진에 존재 |
| '여유시간' 의미 | 실제 남은 시간이 아니라 **루틴 설정의 도착 여유시간** |

`KmaWeatherClient`가 예보를 반환하면 화면이 다시 순위를 계산한다. 예보 실패 시 기상정보는 **미확인**이며 맑음이라고 확정하지 않는다. 점수는 실제 소요시간/정시 도착 확률이 아니라 **후보 정렬용 선호 점수**다. 후보 수집, 시간 상한, 점수식과 정렬은 `RouteRecommendationLabActivity`에서 실험할 수 있다.

## 5. 실시간 도착정보·GPS·알림

### 실시간 도착정보

- 서울시 지하철 `realtimeStationArrival`의 역/호선/방면을 TMAP 선택 구간과 대조. `barvlDt>0`이며 신선하고 방향이 확인된 정보만 실제 ETA로 표시.
- `barvlDt=0` 또는 방향 미확인은 임의의 분으로 환산하지 않음. 현재 UI는 ETA 제목/칸/구분선을 **GONE** 처리하고 열차 상태·방면만 짧게 표시.
- 참고 실측(2026-11-19 20:18 KST, 단일 표본): 12개 역/150행 중 ETA 양수 67건, 0초 83건. **광운대역 1호선 6건 전부 0초**, 여러 역의 2호선 등은 양수 제공. 상시 서비스 가용률로 일반화하지 말 것.
- 버스는 서울시 및 TAGO 정류소/노선 일치 확인. TAGO 인증 403 등의 권한 문제는 실제 승인 상태 확인 필요.

### 위치와 이동 화면

- 전경 화면의 단발성 위치 요청과 사용자가 버튼으로 명시적으로 시작하는 **Foreground TrackingService**를 구분함.
- GPS 정확도·신선도·경로 일치 확인 후 `StagePositionMatcher`로 현재 구간을 판정. `RouteSchematicView`의 파란 위치 마커는 도식상의 **근사 진행률**이지 지도상 실제 좌표 아이콘이 아님.
- 실제 추적 서비스가 종료되는 이유를 사용자 화면에 표시하도록 개선. **알림 테스트 모드가 켜져 있으면 GPS 추적이 시작되지 않음**.
- 일부 위치·환승·하차 상황에서는 GPS 관측 불가/모호성 때문에 표시 및 알림이 생략될 수 있음.

### 알림 10종

| 예약/이동 | 알림 유형 | 확인 조건 |
|---|---|---|
| 예약 | 출발 준비, 출발 권장, 날씨 주의 | 선택 루틴의 목표 시각·계획 소요시간, 최신 기상요약 |
| 이동 중 | 차량 도착 임박, 승차지 출발 | 검증된 양수 ETA, 필요시 최신 GPS·보행시간 |
| 이동 중 | 환승 준비, 하차 준비 | 위치·구간·가까운 정차 위치 확인 |
| 이동 중 | 도착 지연 예상, 경로 이탈, 목적지 도착 | 목표 시각 대비 계획상 지연, 경로 이탈 지속, 목적지 접근 확인 |

세부 임계값/중복 억제/미발송 조건은 [ALARM_FEATURES.md](ALARM_FEATURES.md)에 기술. 테스트/실제 발송은 `AlertCopy` 문구 생성기를 공유하지만, **디버그 모의 발송이 실제 GPS·교통 API 기반 종단간 발송을 검증하지는 않는다**.

## 6. 데이터와 네트워크 흐름

```text
[로컬 설정] local.properties → Gradle BuildConfig → 앱 REST 클라이언트
     ↓
TMAP POI/Transit/Walking · 서울 지하철/버스 · TAGO · KMA
     ↓
network 파서 → model TransitRoute/TransitArrival / KMA Report
     ↓
engine 점수·도착/위치 조건 판단
     ↓
ui 카드/지도/약도 또는 alarms NotificationHelper
     ↓
로컬 SharedPreferences(계정별 루틴·경로 캐시)
Room(알림 기록) / Firestore(사용자 루틴·환경설정만)
```

| 저장소/서버 | 저장되는 내용 | 저장하지 않는 내용 |
|---|---|---|
| Firebase Auth | 로그인 계정 | 대중교통 API 키 |
| Firestore | 사용자 루틴 및 설정 페이로드 | TMAP 상세 경로 좌표·실시간 ETA |
| 계정별 SharedPreferences | 루틴 선택·기본값·알림 설정·23시간 경로 캐시 | 계정 간 공유 상태 |
| Room | 알림 발송·차단 이력 | GPS 장기 이동 로그 전체 |
| 앱 메모리 | 현재 이동 화면의 일시적 API 상태·날씨 캐시 | 영구 경로 이력 |

## 7. 환경 설정과 보안

`app/google-services.json`은 Firebase 앱 설정 파일이며, `local.properties`에는 로컬 SDK 경로 및 다음 키 이름을 설정함: `TMAP_APP_KEY`, `TMAP_TRANSIT_APP_KEY`, `PUBLIC_DATA_SERVICE_KEY`, `KMA_SERVICE_KEY`, `SEOUL_SUBWAY_API_KEY`, `SEOUL_BUS_SERVICE_KEY`, `TAGO_BUS_SERVICE_KEY`. 공공데이터포털 공통키를 사용하는 서비스는 별도의 **활용 승인**이 필요할 수 있음.

`.gitignore`는 `local.properties`, `google-services.json`, 서명키, APK 및 TMAP SDK 바이너리 등을 제외한다. 단, **BuildConfig로 주입하는 REST API 키는 APK에서 추출 가능**하므로 개발/파일럿 구조로만 이해해야 한다. 상용화 시 인증·사용량 제한을 포함한 서버 프록시가 필요하다. Firebase 보안규칙 변경은 Git 커밋만으로 서비스에 배포되지 않는다.

## 8. 테스트와 검증 범위

| 위치 | 검사 가능한 것 | 검사하지 못하는 것 |
|---|---|---|
| `app/src/test/` | 순수 Java 엔진·모델·파서 단위 테스트 | 실제 OS 알림·GPS·API 권한 |
| `debug/AlertLabActivity`, `AlertLabSuite` | 알림 문구·10종 시뮬레이션, 경계조건 | 실제 이동 중 백그라운드 발송 신뢰성 |
| `debug/ApiProbe` | 실제 API 연결·응답 상태 | 앱 전 구간 종단간 성공 |
| `debug/SubwayEtaSurvey` | 12개 역, 노선별 양수/0초/누락 ETA 표본 | 모든 역·시간대의 서비스 수준 |
| `debug/RouteGpsLabActivity` | 저장 경로의 모의 마커 및 좌표 판정 | Android 기기 GPS 조작이나 실제 서비스 수신 |
| `debug/RouteRecommendationLabActivity` | 가상 후보·저장 후보에 대해 **실제 앱과 같은 `rank()`** | 실제 기상특보 수신·TMAP 예측 정확도 |

로컬 개발 환경에서 `assembleDebug`, `assembleRelease`, 전체 JUnit 테스트 75개가 통과했다. Gradle 8.2의 Windows 한글 경로 클래스 로딩을 우회할 경우 `scripts/test-core.ps1`로 전체 테스트를 실행한다. 기상청 좌표 격자 검사는 Android 메인 스레드를 초기화하지 않고 순수 계산을 실행한다. 실제 GPS, 교통 API, Firebase 서버 동기화는 별도 기기 검증 항목이다.

## 9. 미완료·운영 위험

- 기상특보(한파주의보/경보 포함) **공식 API 미연동**. 저온·강수 예보와 공식 특보를 혼동하지 말 것.
- GPS 추적이 활성화되지 않는 실제 기기 환경, 지하철 터널 위치 정확도, 네트워크 중단/절전 백그라운드 동작은 재검증 필요.
- 광운대 등 1호선 실시간 ETA가 0초인 표본 존재. 정확한 실시간 도착시간을 만들어 낼 수 없음.
- 환승 구간 간 경로 연계와 실제 차량 탑승 확정, 전 구간 실시간 ETA는 미완성.
- Firebase 규칙·TAGO 승인·지도 SDK 바이너리는 GitHub 변경만으로 외부 서비스에 적용되지 않음.
- `targetSdk=28` 및 개발용 클라이언트 내 API 키 사용은 최신 배포·보안 요건 미충족 가능성.

## 10. 관련 문서

- [DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md): 개발 단계·브랜치 전략·주요 변경과 이유
- [ALARM_FEATURES.md](ALARM_FEATURES.md): 알림 10종 판단 및 저장
- [ALERT_LAB.md](ALERT_LAB.md): 디버그 실험 모드의 격리 범위
- [API_INTEGRATION_VERIFIED.md](API_INTEGRATION_VERIFIED.md): API 연동·실측 기록
- [UI_LAYOUT_QA.md](UI_LAYOUT_QA.md): 화면 QA 기준
- [STATUS.md](STATUS.md), [REQUIREMENTS_CURRENT.md](REQUIREMENTS_CURRENT.md): 시점별 현황·요구사항(과거 기록 포함)
