# 나만의 출퇴근 — 개발 에이전트 인수인계 및 구현 개요서

> **문서 기준:** 2026-11-14  | **프로젝트:** 서울 한정 출퇴근·통학 루틴 안내 Android 파일럿
> **인수인계 목적:** 신규 개발 에이전트가 기존 테스트 앱의 소스 구조, 실제 확인된 성과, 앞으로 구축해야 할 기능과 구현 우선순위를 파악하고 개발을 이어갈 수 있게 한다.
> **중요:** 아래의 **검증 완료 / 코드 존재 / 미구현 / 제약** 표기를 혼동하지 말 것.

---

## 0. 에이전트에게 전달할 핵심 지시

1. **기존 Java + XML Android 프로젝트를 최대한 재사용**한다. Kotlin/Compose로 전환하지 않는다.
2. 개발환경은 사용자가 말하는 **Android Studio 14(사용 환경에서 AGP 최대 8.2.0 지원)** 기준으로 시작한다. 프로젝트 설정은 **AGP 8.2.0 / Gradle 8.2 / JDK 17 / compileSdk 34 / minSdk 26**이다.
3. 목표는 상용 출시가 아닌 **소수 사용자 대상 저비용·가능하면 무료 파일럿(MVP)** 이다. 새로운 유료 서버나 API 계약을 전제로 설계하지 않는다.
4. **현재 성공이 확인된 TMAP 대중교통 경로와 서울시 따릉이 API 테스트 코드를 보존**한다. 신규 구현 전 기존 코드를 확인하고 기능별로 모듈화한다.
5. 지도 화면에는 **TMAP Android 지도 SDK**, 경로 계산에는 **TMAP REST API**, 대중교통 실시간 도착에는 **서울시 교통 API**를 각각 사용한다. 이 세 기능을 혼동하지 않는다.
6. 기존 테스트 ZIP에 **Gradle Wrapper 실행 파일(`gradlew`, `gradle-wrapper.jar`)이 없음**을 고려한다. 빌드 재현성과 실제 기기 실행은 별도 검증이 필요하다.
7. 실제 검증되지 않은 항목을 '완료'로 기록하지 않는다. API 키·비밀번호는 소스/문서에 기록하지 않는다.

---

## 1. 서비스 정의

### 1.1 문제와 사용자

- **문제:** 직장인, 학생 등 매일 비슷한 경로를 이동하는 사용자가 매번 버스·지하철 도착정보, 날씨, 이동수단을 수동 확인해야 함.
- **해결:** 출발지·목적지·반복 요일·도착 목표시간·선호 경로를 등록하면 이를 기반으로 교통·기상·공유 이동수단 정보를 통합해서 **출발 권장시각을 선제적으로 안내**한다.
- **초기 운영 범위:** 서울특별시 내 출발·도착 경로. 데이터 제공자별 기능을 분리해 추후 타 지역으로 확장 가능하게 설계.
- **예산:** 상한 200만 원이나 **실제 목표 비용은 사실상 0원**. 기존 PC·테스트 기기 보유, 팀 직접 개발, API 무료 한도 내 테스트를 가정.

### 1.2 필수 사용자 흐름

```text
앱 실행 → 로그인/회원가입 → 첫 루틴 등록(출발지/목적지/요일/도착시간)
  → 경로 후보 조회(TMAP) → 경로/교통수단/경유지 설정 → 루틴 저장(Firestore)
  → 메인에서 다음 출발 일정·날씨·교통 요약 → 출발 알림(Android 로컬 알림)
  → 이동 시작 → 지도/GPS/도착정보 확인 → 이동 종료 및 알림 로그 저장
```

### 1.3 파일럿의 완료 기준

- 사용자가 회원가입하고 출퇴근 루틴을 저장/수정할 수 있다.
- 적어도 하나의 경로를 조회해 지도에 그릴 수 있다.
- 대중교통·날씨 정보를 필요한 시점에 조회하고 표시할 수 있다.
- 목표 도착시간과 예상 이동시간으로 출발 알림을 계산·예약·표시할 수 있다.
- 이동 화면에서 사용자의 현재 위치를 표시하고 이동을 종료할 수 있다.
- 따릉이 대여소의 위치와 잔여 수량을 지도에 표시한다.
- API 한도 소진, 권한 거부, GPS 미수신, 오프라인 상태에 대한 예외 처리가 있다.

---

## 2. 실제 구현·검증 현황 (최우선 확인)

### 2.1 검증 완료: 실제 실행 결과가 사용자에 의해 제공됨

| 항목 | 실제 확인 | 해석 |
|---|---|---|
| **서울시 따릉이 API** | HTTP `200`, API `INFO-000`, 대여소 5개 결과. 망원역/합정역 일대 대여 가능 대수와 좌표가 표시됨 | **인증·GET·JSON 파싱 동작 확인**. 제공된 화면은 샘플 5개 조회; 전체 조회·지도 연동은 별도 |
| **TMAP 대중교통 경로** | `POST https://apis.openapi.sk.com/transit/routes` → HTTP `200`, `itineraries` 1건 | **TMAP 대중교통 API 동작 확인** |
| TMAP 응답 상세 | 총 727초, 도보 394초, 총 거리 3,310m, 도보 438m, 환승 1회, 요금 3,250원 | `legs`의 WALK/SUBWAY/BUS, 역·정류장 목록 및 `passShape.linestring` 좌표 반환 확인 |
| Android 실행 | Android Studio에서 앱 실행·API 결과 표시 경험 있음 | 테스트 앱 일부 동작 확인. **전체 기능 완성 아님** |

**실제 TMAP 테스트 경로:** 출발지 → 신논현역(도보) → 신분당선으로 양재역 → 양재역 버스 정류장(도보) → 서초18 등 버스 구간 → 말죽거리공원사거리 → 도착지(도보). `Lane`에 대체 노선도 포함됨.

### 2.2 코드 존재하지만 성공이 따로 확인되지 않은 테스트

- **TMAP POI GET**: 장소 검색 입력 UI 및 HTTP 호출 코드 있음. 사용자 실측 성공 응답 미제공.
- **TMAP 보행자 경로 POST**: 출발·도착 좌표 입력 및 호출 코드 있음. 사용자 실측 성공 응답 미제공.
- **TAGO 공유 PM GET**: 공식 명세 URL을 입력하는 범용 테스트 UI 있음. 실제 서울 기기 데이터/응답 성공은 미검증.
- **서울시 따릉이 정식 인증키 20건 조회**: 테스트 코드 존재. 정식 키를 이용한 성공 여부는 미확인.

### 2.3 아직 구현되지 않은 앱 기능

Firebase 로그인/Firestore, TMAP 지도 SDK 화면, 위치추적 Foreground Service, 기상청 연동, 버스·지하철 실시간 도착 연동, 출발 알림 예약, Room 알림 로그, 실사용 루틴 UI, 경유지·편의시설 통합 표시, 출퇴근 경로를 지도에 그리는 기능은 **미구현**.

---

## 3. 기존 소스 파일과 실행환경

### 3.1 인수인계할 프로젝트 아카이브

**`SeoulMobilityApiTester_TMAP_Java_AGP82.zip`** — 기존 따릉이·TAGO 시험 UI에 TMAP REST 시험 UI를 추가한 Java Android 프로젝트.

```text
SeoulMobilityApiTester/
├── README_먼저읽기.md
├── build.gradle                  # com.android.application 8.2.0
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties # Gradle 8.2 다운로드 URL
└── app/
    ├── build.gradle              # compileSdk 34, minSdk 26, targetSdk 28(임시)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/seoulmobilitytester/
        │   ├── MainActivity.java       # 따릉이·TAGO 테스트 및 TMAP 화면 이동
        │   ├── TmapTestActivity.java   # POI/보행자/대중교통 API 테스트
        │   └── TmapApiClient.java      # TMAP HTTP GET/POST 공통 호출
        └── res/
            ├── layout/activity_main.xml
            ├── layout/activity_tmap_test.xml
            └── values/strings.xml
```

- **패키지 및 applicationId:** `com.example.seoulmobilitytester`
- **Manifest:** `INTERNET` 권한, `MainActivity` launcher, `TmapTestActivity` 등록, `usesCleartextTraffic=true`.
- **설정 주의:** `targetSdk 28`은 기존 HTTP API 시연용 임시 설정이지 Android 14 상용 대상 SDK가 아니다. 목표 SDK 상향 때는 Android 12~14 권한·알림·서비스 정책 재검토가 필요하다.
- **Wrapper 주의:** 아카이브에 `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`가 없음. 재현 가능한 프로젝트로 만들 때 정상적인 Gradle 8.2 Wrapper를 복구해야 한다.
- **빌드 상태:** 생성 환경에서 APK 실제 빌드까지 검증된 자료는 없음. 사용자는 프로젝트를 실행해 따릉이와 TMAP 응답을 확인했다고 보고함.

### 3.2 코드 확장 시 권장 패키지 구조

```text
com.example.seoulmobilitytester/
├── ui/
│   ├── MainActivity.java
│   ├── LoginActivity.java
│   ├── CustomActivity.java
│   ├── MapActivity.java
│   ├── TrackingActivity.java
│   └── AlarmLogActivity.java
├── ui/fragments/         # 최초 루틴 등록, 경유지/시설 목록 등
├── api/
│   ├── TmapApiClient.java
│   ├── TmapTransitRepository.java
│   ├── SeoulTransitRepository.java
│   ├── BikeRepository.java
│   ├── WeatherRepository.java
│   └── SharedPmRepository.java
├── data/
│   ├── model/            # Routine, Route, Leg, BikeStation, Arrival 등
│   ├── remote/           # Firebase Auth/Firestore
│   └── local/            # Room entities/DAO, SharedPreferences
├── location/             # LocationTracker, TrackingService
├── alarm/                # DepartureScheduler, AlarmReceiver
└── domain/               # DepartureTimeCalculator, RouteSelectionEngine
```

이는 **목표 구조**이지 현재 ZIP에 실제 존재하는 파일 목록이 아니다. 기존 프로젝트 패키지 이동 시 AndroidManifest 및 import를 함께 변경한다.

---

## 4. 화면별 구현 명세 (목표 구조)

| 화면 | 컴포넌트 및 UI | 핵심 기능 | 외부 데이터/저장 | 화면 전환 |
|---|---|---|---|---|
| **① 메인** `MainActivity` | 루틴 카드, 날씨·교통 카드, 하단 내비게이션. Activity + 재사용 Fragment | 오늘 루틴, 권장 출발시각, 출발 알림 상태 | Firestore, 서울시 교통, 기상청 | 커스텀/지도/이동/로그 |
| **② 로그인** `LoginActivity` | 이메일·비밀번호 폼, 가입 UI. Activity/Fragment | 가입·로그인·자동 로그인 | Firebase Authentication | 인증 성공→메인 |
| **③ 커스텀** `CustomActivity` | 최초 등록 Fragment, 교통수단 Dialog, 경유지 BottomSheet, 경로 RecyclerView | 출발·도착·요일·도착시간, 경로선택, 경유지, 편의시설/선호교통 | TMAP 경로/POI, Firestore | 지도 미리보기/메인 저장 |
| **④ 지도** `MapActivity` | TMAP MapView, 마커, 경로 폴리라인, 상세 BottomSheet | 경로 전경, 주변 따릉이·PM, 편의시설, 위치 확인 | TMAP 지도 SDK·POI·경로, 따릉이, TAGO(검증 후) | 커스텀/이동 |
| **⑤ 이동** `TrackingActivity` | 현재 위치 지도, ETA·환승 카드, 이동 종료 | GPS, 경로 진행, 실시간 도착 갱신, 이탈 안내(후속) | Fused Location Provider, Foreground Service, 서울시 교통 | 메인/지도 |
| **⑥ 알람 로그** `AlarmLogActivity` | RecyclerView, 날짜 필터, 상세 Dialog | 알림 조회·삭제, 출발·지연 이력 | Room(기기 로컬) | 메인/관련 루틴 |

**UI 구현 원칙:** XML을 `inflate`하여 View를 생성하고, 독립적인 전체 화면은 Activity, 화면 일부 교체는 Fragment, 선택/상세 팝업은 `AlertDialog` 또는 `BottomSheet`를 사용한다. 화면 간 이동은 Intent/FragmentManager로 처리한다.

---

## 5. 외부 API 및 기술 목록

### 5.1 외부 API

| 제공자/기능 | 요청·인증 | 입력 | 주요 응답/활용 | 검증/유의점 |
|---|---|---|---|---|
| **TMAP 지도 SDK** | Android SDK + 앱키 | 중심 위경도, 마커, 경로 좌표 | 지도 화면, 마커, 폴리라인 | **SDK 미설치**. Raster/Vector SDK 설치·Java·AGP 호환성 확인 필요 |
| **TMAP POI 검색** | GET `/tmap/pois?version=1...`; Header `appKey` | `searchKeyword`, `count`, 중심 좌표/조건 | 장소명, 주소, 위경도, 분류 | 테스트 코드 있음, 성공 미확인 |
| **TMAP 보행자 경로** | POST `/tmap/routes/pedestrian?version=1`; Header `appKey` | 출발·도착 경위도, 이름, 좌표계 | GeoJSON 계열 features, 거리·시간·geometry | 테스트 코드 있음, 성공 미확인 |
| **TMAP 대중교통** | POST `https://apis.openapi.sk.com/transit/routes`; Header `appKey` | `startX/startY/endX/endY`, `count`, `lang`, `format` | `metaData.plan.itineraries[]`, `totalTime`, `legs[]`, `transferCount`, `fare`, `passShape.linestring` | **실제 HTTP 200 확인**. 별도 상품 권한·일 호출 한도 주의 |
| **TMAP 자동차 경로** | TMAP 경로 REST, POST | 출발·도착 좌표 및 탐색 옵션 | 총시간·거리·경로 | 설계만 존재, 명세 확인 후 구현 |
| **서울시 지하철 실시간 도착** | 서울시 Open API GET, 키 | 역 이름·조회 범위 | `statnNm`, `trainLineNm`, `arvlMsg2`, `barvlDt` 등 | 아직 미구현·실시간 정확도/호출 한도 유의 |
| **서울시 버스 도착** | 서울시 버스정보 GET, 서비스키 | 정류장 고유 ID, 노선 ID(필요시) | 도착예정시간, 남은 정류장 수 | 아직 미구현; TMAP 정류장 ID와 매핑 검증 필요 |
| **서울시 따릉이** | GET `http://openapi.seoul.go.kr:8088/{KEY}/json/bikeList/1/1000/` | 인증키, 조회 범위 | `stationName`, `parkingBikeTotCnt`, `stationLatitude/Longitude` | **실제 샘플 조회 성공**. HTTP 평문 대응 필요 |
| **국토부 TAGO 공유 PM** | 공공데이터포털 REST, 서비스키 | 공식 개별 API 명세 참조 | 운영사·기기 ID·배터리·좌표(제공 범위 내) | 범용 URL 테스터만 존재, 서울 데이터 제공 여부 미확인 |
| **기상청 단기예보** | 공공데이터포털 GET, 서비스키 | 발표일시, 격자좌표 `nx/ny` | `TMP` 기온, `POP` 강수확률, `PTY` 강수형태, `PCP` 강수량 등 (예보 종류에 따름) | 미구현. 초단기실황과 단기예보는 항목이 다름 |

**좌표 주의:** REST 경로 요청의 `X=경도(longitude)`, `Y=위도(latitude)`; Android 지도 SDK 좌표 순서는 해당 SDK의 타입 정의에 따를 것. `linestring`도 `경도,위도` 순서로 파싱한다.

### 5.2 TMAP 실제 수신 구조 예시(핵심 필드만 발췌)

```json
{
  "metaData": {
    "plan": {
      "itineraries": [{
        "totalTime": 727,
        "totalDistance": 3310,
        "totalWalkTime": 394,
        "totalWalkDistance": 438,
        "transferCount": 1,
        "fare": { "regular": { "totalFare": 3250 } },
        "legs": [
          { "mode": "WALK", "sectionTime": 75 },
          { "mode": "SUBWAY", "route": "신분당선", "passShape": {"linestring": "127.024758,37.503958 ..."} },
          { "mode": "BUS", "route": "마을:서초18", "passShape": {"linestring": "127.034744,37.483733 ..."} }
        ]
      }]
    }
  }
}
```

이 코드는 **실제 전달된 응답에서 구조 이해용으로 축약한 예시**이며 완전한 API 응답이 아니다. `totalTime`은 727초이며, `legs[].sectionTime`의 합이 항상 같은 값을 보장하지는 않으므로 총 이동시간 표시는 최상위 `totalTime` 기준으로 한다.

### 5.3 Android 및 저장 기술

| 기술 | 목적 | 구현 예시/제약 |
|---|---|---|
| **Java + XML** | 앱 UI·도메인 로직 | Java Activity/Fragment 및 XML 레이아웃. Kotlin 사용하지 않음 |
| **HttpURLConnection + JSONObject** | REST·JSON 시험/파싱 | 초기 코드의 라이브러리 없는 호출 방식을 재사용 가능; 네트워크는 UI 스레드에서 실행 금지 |
| **Firebase Authentication** | 이메일/비밀번호 로그인, UID | Firebase Android SDK; 아직 미연동 |
| **Cloud Firestore** | 사용자 루틴·선호 경로 저장 | `users/{uid}/routines/{id}`; 보안 규칙으로 사용자별 접근 제한 |
| **Room** | 알림 로그, 필요한 로컬 이력 | 영구 보관이 허용되지 않은 외부 경로 API 원본과 분리 |
| **SharedPreferences** | 소규모 설정 | 알림 활성화 여부, 선택한 루틴 ID 등 |
| **Fused Location Provider** | Android 위치 획득 | 이동 중 현재 위경도 제공; 사용자 권한 필요 |
| **Foreground Service** | 이동 중 위치추적 유지 | 사용자에 의해 명시적으로 시작; Android 14 위치 서비스 제한 준수 |
| **AlarmManager + BroadcastReceiver** | 출발 시각 알림 트리거 | 정확 알람 권한/절전 정책 확인 |
| **NotificationManager** | 실제 알림 표시 | Android 13 이상 알림 권한·채널 고려 |
| **WorkManager** | 지연 가능 작업 | 정확한 분 단위 실시간 출발 보장 수단이 아님 |
| **RecyclerView/BottomSheet/Fragment** | 동적 카드·목록·선택 UI | 루틴·POI·대여소·로그 목록, 세부 설정 |

---

## 6. 데이터 모델 제안

### 6.1 Firestore (사용자 설정과 루틴)

```text
users/{uid}
  ├── profile: { displayName, createdAt, settings }
  └── routines/{routineId}
        ├── name, enabled, weekdays, desiredArrivalTime
        ├── start: { name, lat, lon }
        ├── destination: { name, lat, lon }
        ├── preferredTransport, marginMinutes
        ├── selectedStops: [ { provider, providerStopId, name } ]
        ├── viaPoints: [ { name, lat, lon } ]
        └── createdAt, updatedAt
```

**중요:** `TMAP legs/passShape/전체 응답`을 루틴과 함께 영구 저장하는 설계는 피한다. 사용자 정의 경유지, 출발지, 목적지, 선호 설정과 외부 경로 응답을 구분하고 **TMAP 저장·재사용 약관**을 적용한다.

### 6.2 Room

```text
NotificationLog(id, routineId, type, title, message, occurredAt, delivered)
TripRecord(id, routineId, startedAt, endedAt, actualDurationSec)
```

### 6.3 화면/비즈니스 로직에서 사용하는 모델

- `Routine` : 사용자 설정
- `RouteCandidate` : 총시간, 총거리, 요금, 환승수, 구간 리스트, 데이터 취득시각
- `RouteLeg` : WALK/SUBWAY/BUS, 출발·도착, 소요시간, 좌표 목록
- `TransitArrival` : 노선, 정류장/역, 도착예정정보, 데이터 갱신시각
- `BikeStation` : ID, 이름, 위경도, 이용 가능 수량
- `WeatherSnapshot` : 격자/시간/기온/강수 정보
- `NotificationLog` : 발송유형, 시각, 상태

---

## 7. 루틴 분석 및 알림 로직

### 7.1 기본 계산

```text
출발 권장시각 = 목표 도착시각 - 예상 이동시간 - 사용자 여유시간
```

**예시:** 09:00 도착 / 45분 예상 이동 / 여유 10분 → **08:05 출발 권장**.

### 7.2 실제 구현에서는 추가 검증

1. 선택한 경로의 버스·지하철 탑승 구간을 식별한다.
2. 서울시 API의 실시간 도착정보와 **정류장·노선/방향·역 ID의 실제 매핑**을 확인한다.
3. 도보 접근시간과 탑승 가능한 차량 시각을 대조한다.
4. 날씨/환승/지연에 대한 보수적 여유시간 규칙을 적용한다.
5. 이동 시간이 크게 변하면 알림을 재계산하되 API 요청 간격과 배터리 소모를 제한한다.

**중요한 제약:** 서버 없는 파일럿에서 앱이 완전히 종료된 상태에도 끊임없이 외부 API를 조회해 실시간 최적 출발시각을 유지하는 것은 보장할 수 없다. 1차는 **사전 계산된 로컬 알림 + 사용자가 앱을 열었을 때 실시간 갱신**으로 정의한다. 장시간 GPS 추적은 이동 시작 중에만 활성화한다.

---

## 8. 개발 단계 및 우선순위 (완료 조건 포함)

| 단계 | 개발 과제 | 완료 판단 |
|---|---|---|
| **0. 기반 정리** | 기존 ZIP 열기, Wrapper 복구, JDK/Gradle 확인, 기존 테스트 회귀 점검 | 최소 한 기기/에뮬레이터에서 따릉이·TMAP 테스트 기능 정상 실행 |
| **1. 데이터 모듈화** | TMAP/서울시/Bike API 공통 요청·응답 모델 분리, 에러·시간초과 처리 | UI와 네트워크 코드 분리, 키 유출 없이 오류 표시 |
| **2. 지도 SDK** | TMAP SDK 설치, MapActivity 지도 표시, 따릉이 마커 | 지도 확대·이동 + 실제 대여소 마커 표시 |
| **3. 경로 표시** | TMAP 대중교통 `legs/passShape` 파싱, 도보·POI 검증, 폴리라인 표시 | 노선별 구간이 지도 위에 올바르게 표시 |
| **4. 사용자·루틴** | Firebase Auth/Firestore, 로그인·루틴 등록·수정 | 앱 재실행 후 내 루틴을 조회·수정 가능 |
| **5. 메인/커스텀 UI** | 반복 요일/시각/교통수단/경유지 설정, 오늘의 루틴 표시 | 사용자 흐름이 주요 화면 간 연결됨 |
| **6. 실시간 교통·날씨** | 서울시 지하철·버스, 기상청 연동과 ID/격자 매핑 | 테스트 역·정류장의 실시간 정보를 표시 |
| **7. 알림·이동** | 출발시간 계산, 로컬 알림, GPS·Foreground Service | 알림이 예약·표시되고 이동 시작/종료 작동 |
| **8. 로그·안정화** | Room 로그, 권한·오프라인·한도 처리, 무료 호출량 관리 | 일반 오류 상황에서 앱이 중단되지 않음 |
| **9. 후속 기능** | TAGO 실제 데이터 검증, 편의시설 최적 경유, 경로 재탐색 | 별도 검증 후 채택 |

**설계상 우선순위:** 먼저 실제 API 성공을 토대로 **지도 SDK + 따릉이 마커 + 경로 폴리라인**을 구현한다. 이후 Firebase·루틴·알림으로 확장한다. Firebase 비용은 Spark 무료 할당량을 중심으로 관리한다.

---

## 9. 알려진 제약·기술적 위험

| 문제 | 대응 |
|---|---|
| TMAP 대중교통 무료 호출량 적음 | 사용자 명시 동작에서만 재탐색, 테스트 호출 로깅; 여러 키로 제한 우회하지 않음 |
| 앱 키가 APK에 노출될 수 있음 | 개인 파일럿용 키 최소 권한/사용량 관리; 공개 배포 전 서버 중계 및 키 관리 필요 |
| 기존 따릉이 API는 HTTP | 개발 테스트에서만 cleartext 허용; 향후 안전한 HTTPS 중계 검토 |
| 지도 SDK 설치/버전 이슈 | 실제 선택한 Raster/Vector SDK 공식 가이드에 맞춰 라이브러리 추가. 의존성 임의 지정 금지 |
| `targetSdk 28` 임시 설정 | Android 14 런타임 정책/권한에 맞춰 목표 SDK 상향 작업 분리 |
| 대중교통 API ID 불일치 | TMAP `routeId/stationID`를 서울시 API ID로 바로 사용하지 말고 매핑 검증 |
| 실시간 도착 데이터 오차 | 마지막 갱신시각, 데이터 미수신 안내, 안전 여유시간 |
| GPS/백그라운드 제한 | 이동 중 명시적인 Foreground Service, 종료 시 추적 중지 |
| 지도·경로 응답 보관 제한 | API 제공자 약관에 맞춰 캐시/재조회/저장 정책 설계 |
| Android Studio 호환성 | AGP 8.2.0, Gradle 8.2, JDK 17 유지하고 Wrapper 복구 |
| PM API 제공 범위 | TAGO 공개 API 존재와 서울 업체 실시간 정보가 실제 조회되는지는 별개로 판단 |

---

## 10. 에이전트가 가장 먼저 수행할 작업

1. 전달받은 `SeoulMobilityApiTester_TMAP_Java_AGP82.zip`을 압축 해제하고 실제 Java/XML/Gradle 파일 구조를 읽는다.
2. **Wrapper 파일을 정상화하고 기존 API 테스트 앱을 빌드·실행**해 현 상태를 기준점으로 기록한다.
3. `MainActivity`의 기존 따릉이/TAGO 기능과 `TmapTestActivity`의 POI·보행자·대중교통 테스트를 유지하면서 네트워크 호출을 `api/` 모듈로 분리한다.
4. TMAP Android 지도 SDK의 공식 설치 문서를 기준으로 **Java `MapActivity`** 를 새로 구성하고 기본 지도 표시를 확인한다.
5. 기검증 따릉이 JSON의 좌표·대여 가능 수량을 활용해 **지도 마커를 표시**한다.
6. 기검증 TMAP 대중교통 JSON의 `legs/passShape.linestring`을 파싱해 **폴리라인으로 시각화**한다.
7. 기존 기능 회귀 테스트 결과, 새 구현 목록, 아직 검증되지 않은 부분을 기록한다.

### 에이전트의 산출물 형식

- **실행 가능한 전체 Android Studio Java 프로젝트** (기존 기능 포함)
- 변경/신규 파일 목록, 핵심 로직 설명
- API 키 설정 위치 및 발급키가 없는 경우의 동작
- Gradle Sync / Run 절차
- 테스트 결과표(실행 확인/미확인 엄격히 구분)
- 외부 API 사용량·권한·보안상 주의사항

---

## 11. 공식 자료 / 검증할 문서

- TMAP API 개발자센터: https://tmapapi.tmapmobility.com/
- SK OPEN API: https://openapi.sk.com/
- TMAP 대중교통 API 문서: https://transit.tmapmobility.com/docs/routes
- TMAP 장소 통합 검색: https://tmap-skopenapi.readme.io/reference/장소통합검색
- TMAP 보행자 경로: https://tmap-skopenapi.readme.io/reference/보행자-경로안내
- 서울시 따릉이 실시간 대여정보: https://data.seoul.go.kr/dataList/OA-15493/A/1/datasetView.do
- 서울 열린데이터광장: https://data.seoul.go.kr/
- 공공데이터포털 TAGO 공유 PM: https://www.data.go.kr/data/15117668/openapi.do
- 기상청 단기예보 조회: https://www.data.go.kr/data/15084084/openapi.do
- Firebase Android 설치: https://firebase.google.com/docs/android/setup
- Firebase 요금: https://firebase.google.com/pricing
- Android 위치 서비스: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android 알람: https://developer.android.com/develop/background-work/services/alarms

> **마지막 주의:** 본 문서는 사용자와의 설계 논의, 실제 사용자 제공 API 응답, 전달된 ZIP 내부 소스의 확인 결과를 합친 인수인계 문서다. 공식 URL/가격/무료 사용량/SDK 다운로드 방식은 **구현 시점의 최신 서비스 명세로 다시 확인**할 것. 기술적으로 가능하다는 주장과 현재 코드에서 구현·검증됐다는 주장을 구분할 것.
