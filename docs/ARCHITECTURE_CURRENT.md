# 나만의 출퇴근 — 현재 아키텍처·기능·파일 역할

> 2026-11-23 로컬 앱 0.2.0 기준입니다. [현재 검증 결과](STATUS.md)와 [개발 이력](history/DEVELOPMENT_HISTORY.md)을 함께 참고하세요.

## 1. 목적과 범위

이름 있는 출근/퇴근 경로를 등록하여 목표 도착 시각, 경유지, 보행 구간, 대중교통 도착정보, 날씨와 GPS를 결합하는 Android 통근 보조 앱이다. 고정 루틴 이용자가 필요한 시점에 이동 상태와 알림을 받는 것을 목표로 한다. **자동 운행 제어·승차 확정·전 구간 실시간 내비게이션은 현재 지원하지 않는다.**

- 플랫폼: Java 17, Android XML, Android Studio, AGP 8.2.0/Gradle 8.2.
- SDK 설정: `compileSdk=34`, `minSdk=26`, **`targetSdk=28`**. 최신 Play 배포 요건과는 별개 검토 필요.
- 네임스페이스·패키지: `com.example.mycommute`.
- 주요 의존성: Firebase Auth/Firestore, AndroidX/AppCompat/Material, Fused Location, Room, osmdroid, 선택 설치하는 TMAP Raster SDK.

## 2. 주요 사용자 흐름

```text
Firebase 로그인 / 회원가입
  └─ 메인: 다음 목표 도착 일정의 기본 방향, 출근·퇴근 탭 전환, 알림 로그 열기
     ├─ 커스텀: 이름 있는 루틴, 장소·경유지(최대 2개), 요일·도착시각·여유시간
     │   ├─ TMAP POI 검색 → 장소 좌표 선택
     │   ├─ TMAP 대중교통 경로 검색(명시적으로 호출)
     │   ├─ 기상청 단기예보 반영 → 경로 후보 순위 산정
     │   ├─ 추천 1개 기본 표시 / 다른 후보 접기·펼치기
     │   └─ 경로 선택·저장 → 로컬 캐시 및 루틴 설정
     ├─ 지도: 경로 선형·마커, TMAP Raster SDK 사용 시 지도 / 미설치 시 OSM 대체
     ├─ 실시간 이동: 선택 경로 도식, 예보, 첫 탑승 차량 도착정보, 현재 위치
     │   └─ 자동 출발 시간대 또는 수동 시작 → 출발지 반경 확인 → 이동 추적과 조건부 알림
     ├─ 알림 기록: Room
     └─ 설정: 시간 표기, 자동 위치 추적, 출퇴근 위치 안내 표시, 알림 기준
```

### 화면과 연결 파일

| 기능 | 주요 파일 | 역할 |
|---|---|---|
| 로그인·회원가입 | `ui/LoginActivity.java`, 해당 `res/layout/` | Firebase 인증 기반 진입. 데모 우회 로그인 없음 |
| 메인/루틴 전환 | `ui/MainActivity.java`, `activity_main.xml` | 출근·퇴근 및 기본 경로 선택 |
| 루틴 편집/추천 | `ui/CustomActivity.java`, `activity_custom.xml`, `item_route_result.xml` | 장소·경유지·목표시각·반복요일 입력, 경로 후보 카드 렌더링·선택 |
| 장소 검색 | `ui/PlaceSearchActivity.java`, `network/TmapPoiClient.java` | Activity Result로 장소명·위도·경도를 루틴의 요청한 장소 필드에 전달 |
| 지도 | `ui/MapActivity.java`, `ui/TmapRasterMapAdapter.java` | 선택 경로 표시; 공급자 SDK 미설치/실패 시 OSM fallback |
| 실시간 이동 | `ui/TrackingActivity.java`, `ui/CommuteLiveCoordinator.java`, `ui/RouteSchematicView.java` | 선택된 경로, 현재/다음 차량, 지도 이동, GPS 마커·상태·날씨 |
| 위치 서비스 | `location/TrackingService.java` | 자동·수동 시작의 출발지 검사, 위치 업데이트와 이동 중 알림 판단 |
| 설정 | `ui/SettingsActivity.java`, `activity_settings.xml` | 12/24시간제, 자동 추적, 출퇴근 위치 안내 표시, 전체·개별 알림 기준 |
| 알림 이력 | `data/AlarmLogRepository.java` 및 Room 관련 클래스 | 발송/차단 이력 저장·조회 |
| 하단 메뉴 | `ui/Navigation.java`, `view_bottom_nav.xml` | 화면 간 이동 |

## 3. 실제 코드의 패키지·파일 역할

핵심 파일을 기능별로 배치했습니다. 전체 파일은 [프로젝트 README](../README.md)의 파일 트리를 참고하세요.

```text
MyCommuteAndroid/
├─ README.md                    # 실행 설정, 현재 기능 및 문서 링크
├─ settings.gradle              # :app 단일 Android 모듈 선언
├─ build.gradle / gradle.properties / gradle/wrapper/
├─ app/
│  ├─ build.gradle
│  ├─ libs/                     # 별도로 설치하는 TMAP SDK
│  └─ src/main/
│     ├─ AndroidManifest.xml    # 권한·화면·서비스·수신기
│     ├─ java/com/example/mycommute/
│     │  ├─ ui/                 # 화면·경로 도식·지도
│     │  ├─ model/              # 루틴·경로·도착정보
│     │  ├─ data/               # 루틴·설정·Room·Firestore
│     │  ├─ network/            # TMAP·서울시·TAGO·기상청 API
│     │  ├─ engine/             # 추천·출발·승차·위치 판정
│     │  ├─ alarms/             # 예약과 이동 알림
│     │  └─ location/           # 포그라운드 GPS 서비스
│     └─ res/                   # 화면 레이아웃·스타일·네트워크 설정
├─ docs/                        # 현재 동작·설치·검증 문서와 개발 이력
└─ firestore.rules              # Firestore 보안 규칙
```

### 핵심 클래스 책임

| 경로 | 책임과 주의사항 |
|---|---|
| `data/RoutineStore.java` | 계정 단위 SharedPreferences, 출근/퇴근별 복수 루틴, 선택/기본 경로, 기존 슬롯 호환 |
| `data/FirestoreSync.java` | `users/{uid}/routines/{id}`, `users/{uid}/settings/commute`에 사용자 설정 동기화. 공급자 경로 좌표는 업로드하지 않음 |
| `data/TransitRouteCache.java` | 선택 경로의 TMAP 상세 응답을 사용자별 로컬에서 **23시간 TTL**로 캐시, 장소나 선택 경로가 바뀌면 무효화 |
| `data/PinnedRouteRefresh.java`, `data/RouteRefreshJob.java` | 저장한 경로 선택값과 새 조회 결과를 대조해 상세 경로 자동 갱신 |
| `alarms/AutoCommute.java` | 기본 출근·퇴근 경로의 백그라운드 갱신, 위치 추적 시작·종료 예약 |
| `data/TransitRouteMemory.java` | 프로세스 내 선택 후보/경로 관리 |
| `model/TransitRoute.java` | TMAP 대중교통 구간·도보·환승·총시간·좌표/정차역·캐시 직렬화 |
| `model/TransitArrival.java` | 검증된 ETA, 열차 상태, 도착정보 신선도. 상태 정보와 수치 ETA 구분 |
| `network/TmapTransitClient.java`, `TmapWaypointRouteClient.java` | 명시적 대중교통 경로 검색, 경유지마다 구간 분할 후 합성(경유지 0/1/2개 → 검색 1/2/3회) |
| `network/TmapWalkingClient.java` | 현재 GPS → 승차지까지 도보 경로/시간 조회 |
| `network/LiveArrivalClient.java`, `ArrivalMatcher.java`, `SeoulBusClient.java`, `SeoulXmlParser.java` | 서울 지하철·버스 / TAGO 도착정보 호출·파싱, 승차지·노선·방향 교차 검증 |
| `network/KmaWeatherClient.java` | 기상청 단기예보에서 강수형태·확률·기온 확인. **공식 한파주의보/경보 API 미연결** |
| `engine/WeatherRouteRanker.java` | 경로별 선호 점수·추천 순위·최단 대비 추가시간 범위. 루틴 화면에서 `rank()` 호출 |
| `engine/StagePositionMatcher.java` | GPS 신선도·정확도, 경로 구간 매칭; 모호하면 판정 보류 |
| `engine/CommuteWindow.java`, `PlanSchedule.java` | 목표 도착일·시각, 출발 계획, 시간창 계산 |
| `engine/BoardingFeasibilityEngine.java`, `AlertThresholds.java` | 검증된 위치·ETA·도보시간으로 승차 여유/알림 조건 검사 |
| `alarms/ReminderScheduler.java`, `ReminderReceiver.java` | 출발 준비·출발 권장·날씨 관련 예약 알림(AlarmManager) |
| `alarms/TravelAlertMonitor.java` | GPS 서비스에서 도착 임박·승차지 출발·환승·하차·지연·이탈·종료 평가 |
| `alarms/AlertCopy.java` | 출퇴근 알림의 제목과 본문 생성 |
| `alarms/NotificationHelper.java`, `AlertLedger.java` | 알림 채널·발송, 통근/유형/구간 중복 발송 방지 |

## 4. 경로 검색·추천의 정확한 동작

1. 사용자가 커스텀에서 장소·경유지·목표 도착시각·여유시간을 저장하고 **경로 검색 버튼**을 눌러야 TMAP 조회가 시작됨.
2. TMAP 후보에서 각 경로의 `durationMinutes`, `walkingMinutes`, `transfers`를 취득함.
3. 기상 적용이 켜져 있고 유효한 기상청 예보를 받았을 때 비·눈 및 기온을 가중치에 반영함. 아직 **공식 한파특보 자체는 반영하지 않음**.
4. `WeatherRouteRanker.rank()`가 최단 소요시간 기준 **허용 범위 내 경로를 먼저 배치**하고, 각 그룹 내 점수가 낮은 순으로 정렬함. 그중 첫 경로가 화면 상단의 추천 1개이며 나머지는 접힌 컨테이너로 표시함.
5. 추천 1위는 자동으로 사용자의 **선택 경로가 되는 것이 아니다**. 경로 선택을 명시적으로 해야 `selected` 및 캐시가 갱신됨.
6. 최초 선택 후 장소·경유지 등 루틴과 경로 선택 해시를 유지한다. 기본 출근·퇴근 경로는 예정 출발 30분 전을 기준으로 이동 종료까지 유효한 상세 데이터가 없으면 Android의 네트워크 작업에서 다시 조회한다. 홈·지도·이동 화면에서 상세 경로가 없을 때도 조회한다. 동일 경로가 응답에 없으면 선택을 보존하고 화면에 상세 경로를 표시할 수 없다고 안내한다. [TMAP 대중교통 API 이용약관](https://transit.tmapmobility.com/terms)

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
| 한파특보 플래그(엔진 계산용) | 도보 +2.0, 야외 대기 +2.5 |
| 영하 기온(한파 플래그가 없는 경우) | 도보 +1.0, 야외 대기 +1.5 |
| 환승 가중치 | 여유 0~7분: 3 / 8~14분: 6 / 15분 이상: 8 |
| 우선 검토 경로의 추가 소요시간 | `min(12, floor(도착 여유시간/2))` 분 |
| 실제 후보의 야외 대기시간 | **현재 0으로 입력**: 독립 산출 미구현 |
| 자전거·킥보드 후보 | 실제 대중교통 후보에 포함되지 않음; 악천후 자전거 배제는 엔진에 존재 |
| '여유시간' 의미 | 실제 남은 시간이 아니라 **루틴 설정의 도착 여유시간** |

`KmaWeatherClient`가 예보를 반환하면 화면이 다시 순위를 계산한다. 예보 실패 시 기상정보는 **미확인**이며 맑음이라고 확정하지 않는다. 점수는 실제 소요시간/정시 도착 확률이 아니라 **후보 정렬용 선호 점수**다.

### 기상에 따른 출발 시각

- `WeatherDepartureJob`은 기상정보 반영을 켠 기본 출근길·퇴근길의 기상청 단기예보를 백그라운드에서 조회합니다. 등록 출발지와 기본 출발 시각이 속한 정시 예보를 사용합니다.
- `DepartureSchedule`은 계정·루틴·목표 도착 회차·출발지별로 결과를 저장합니다. 기본 출발 시각에서 비·소나기 또는 강수확률 60% 이상은 5분, 눈·진눈깨비는 10분을 뺍니다. 기온 0°C 이하는 5분을 더 빼며 합계는 15분이 상한입니다.
- 조회는 기본 출발 시각의 **준비 알림 설정값과 30분 중 큰 값 + 60분 전**부터 시도합니다. 같은 기준에 15분을 더한 시각에 그 회차의 예보 갱신을 마감합니다. 실패하면 마감 전 15분 간격으로 재시도하고 만료된 회차는 기본 계획을 사용합니다.
- 홈, 실시간 이동, 예약 알림, 자동 위치 추적은 동일한 회차의 기상 보정값을 읽습니다. 이동 경로와 목표 도착 시각은 사용자가 저장한 값을 사용합니다. [출발 시각 계산 예시](USER_GUIDE.md#기상에-따른-출발-시각)

## 5. 실시간 도착정보·GPS·알림

### 실시간 도착정보

- 서울시 지하철 `realtimeStationArrival`의 역·호선·방면을 TMAP 선택 구간과 대조합니다. 신선한 `barvlDt`가 양수이면 남은 시간을 표시합니다. 숫자 필드가 비어 있으면 같은 열차의 명시적인 분·초 메시지를 사용하고, 생성 이후 경과 시간을 차감합니다.
- 정거장 수만 제공되면 `2정거장 전 · 송파`처럼 위치를 표시하고 `도착 예정 시간 미제공`을 별도로 안내합니다. 확인한 방면은 카드 전체 너비에 표시합니다.
- 참고 실측(2026-11-19 20:18 KST, 단일 표본): 12개 역/150행 중 ETA 양수 67건, 0초 83건. **광운대역 1호선 6건 전부 0초**, 여러 역의 2호선 등은 양수 제공. 상시 서비스 가용률로 일반화하지 말 것.
- 버스는 서울시 및 TAGO 정류소/노선 일치 확인. TAGO 인증 403 등의 권한 문제는 실제 승인 상태 확인 필요.

### 위치와 이동 화면

- 자동 위치 추적을 켜고 위치를 허용하면 예정 출발 30분 전부터 **Foreground TrackingService**가 기본 경로의 출발지 반경 500m를 확인한다. 이 시간 안에 최근 60초의 정확도 100m 이내 위치가 확인된 회차만 실제 안내와 예약 알림을 시작한다. 출발 시각까지 확인하지 못하면 위치 확인을 종료한다. 확인된 회차는 도착 감지 또는 목표 도착 시각 30분 후 종료한다. 화면 밖 확인에는 Android 10 이상에서 백그라운드 위치 권한이 필요하다.
- 이동 화면은 선택한 저장 경로를 보여준다. 이동 안내를 시작하기 전 최신 위치가 출발지 반경 밖이면 출근·퇴근 방향에 맞는 안내 문구를 표시한다. `출퇴근 위치 안내 표시` 설정은 화면 문구에만 적용된다. 수동 추적도 반경 확인 후 이동 안내를 시작하며, 위치를 확인할 수 없으면 최대 30초 뒤 종료한다.
- 홈은 현재 시각 이후 돌아오는 목표 도착 일정이 더 가까운 방향의 기본 경로를 보여준다. 방향 탭으로 현재 홈에서 보는 경로를 바꿀 수 있다.
- 예약과 표시의 기준 시간대는 서울이다. 기기별 12/24시간제 선택은 화면과 새 알림 표시에 적용되며 루틴의 저장 시각과 예약은 그대로 유지한다.
- GPS 정확도·신선도·경로 일치 확인 후 `StagePositionMatcher`로 현재 구간을 판정. `RouteSchematicView`의 파란 위치 마커는 도식상의 **근사 진행률**이지 지도상 실제 좌표 아이콘이 아님.
- 실제 추적 서비스가 종료되는 이유를 사용자 화면에 표시하도록 개선.
- 일부 위치·환승·하차 상황에서는 GPS 관측 불가/모호성 때문에 표시 및 알림이 생략될 수 있음.

### 알림 10종

| 예약/이동 | 알림 유형 | 확인 조건 |
|---|---|---|
| 예약 | 출발 준비, 출발 권장, 날씨 주의 | 선택 루틴의 목표 시각·계획 소요시간, 최신 기상요약 |
| 이동 중 | 차량 도착 임박, 승차지 출발 | 검증된 양수 ETA, 필요시 최신 GPS·보행시간 |
| 이동 중 | 환승 준비, 하차 준비 | 위치·구간·가까운 정차 위치 확인 |
| 이동 중 | 도착 지연 예상, 경로 이탈, 목적지 도착 | 목표 시각 대비 계획상 지연, 경로 이탈 지속, 목적지 접근 확인 |

알림의 기준, 중복 방지, 발송 조건은 [알림 동작](ALARM_FEATURES.md)에 정리했습니다.

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

## 8. 빌드와 실행 확인

[현재 검증 결과](STATUS.md)에 빌드 결과와 기기·서버 확인 범위를 정리했습니다.

## 9. 미완료·운영 위험

- 기상특보(한파주의보/경보 포함) **공식 API 미연동**. 저온·강수 예보와 공식 특보를 혼동하지 말 것.
- GPS 추적이 활성화되지 않는 실제 기기 환경, 지하철 터널 위치 정확도, 네트워크 중단/절전 백그라운드 동작은 재검증 필요.
- 광운대 등 1호선 실시간 ETA가 0초인 표본 존재. 정확한 실시간 도착시간을 만들어 낼 수 없음.
- 환승 구간 간 경로 연계와 실제 차량 탑승 확정, 전 구간 실시간 ETA는 미완성.
- Firebase 규칙·TAGO 승인·지도 SDK 바이너리는 GitHub 변경만으로 외부 서비스에 적용되지 않음.
- `targetSdk=28` 및 개발용 클라이언트 내 API 키 사용은 최신 배포·보안 요건 미충족 가능성.

## 10. 관련 문서

- [DEVELOPMENT_HISTORY.md](history/DEVELOPMENT_HISTORY.md): 개발 단계·브랜치 전략·주요 변경과 이유
- [ALARM_FEATURES.md](ALARM_FEATURES.md): 알림 10종 판단 및 저장
- [API_INTEGRATION_VERIFIED.md](history/API_INTEGRATION_VERIFIED.md): 이전 API 실측 기록
- [UI_LAYOUT_QA.md](UI_LAYOUT_QA.md): 화면 QA 기준
- [STATUS.md](STATUS.md), [REQUIREMENTS_CURRENT.md](REQUIREMENTS_CURRENT.md): 현재 실행 결과와 과제 요구사항
