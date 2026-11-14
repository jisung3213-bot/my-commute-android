# 나만의 출퇴근 — Android Java/XML MVP

**문서 기준:** 사용자 제공 `기초 계획안 - Google Docs.pdf`(6개 화면, 공통 기술). 추가 첨부 `전달.zip`은 ZIP 항목이 0개인 빈 파일이었으므로 기존 프로젝트를 수정한 것이 아니라 신규 프로젝트를 작성함.

## 빠른 실행

1. Android Studio에서 이 폴더(`MyCommuteAndroid`)를 프로젝트로 열어. **JDK 17 이상**, Android SDK Platform **35**를 준비해.
2. Gradle **8.9** 및 Android Gradle Plugin **8.7.3** 구성이야. 이 전달물에는 `gradle-wrapper.properties`만 포함되고 **Gradle wrapper JAR/실행 스크립트는 포함되지 않아.** 로컬 Gradle 8.9 환경에서 프로젝트 루트에서 `gradle wrapper --gradle-version 8.9` 실행해 wrapper를 생성해야 해. 이후 Android Studio Sync 및 `./gradlew assembleDebug`(Windows: `gradlew.bat assembleDebug`)를 실행할 수 있어.
3. Firebase 설정 전에는 로그인 화면의 **데모 모드로 시작**을 눌러 로컬 기능을 시험할 수 있어. 이것은 실명 인증/계정 기능이 아니며 모든 루틴/로그는 기기에 저장돼.
4. '출퇴근 루틴 등록·편집'에서 출발지/도착지, 계획 출발 시각, 일반/우천 예상 소요시간, 요일을 입력하고 저장해. 기본 화면·지도·이동 화면·알림 로그로 이동해.
5. 알림 테스트에는 Android 알림 권한과 **정확한 알람 특별 접근 권한**을 검토해야 해. 권한이 없으면 부정확한 시각으로 실행될 수 있어.
6. `local.properties.example`를 참고해 **프로젝트 루트의 실제 `local.properties`**에 TMAP 키를 추가할 수 있어. Firebase는 `app/google-services.json`을 직접 넣어야 하고 Firebase 콘솔에서 이메일/비밀번호 로그인을 켜야 해. 상세 내용은 [`docs/API_SETUP.md`](docs/API_SETUP.md).

> 중요: 이 패키지는 **소스 프로젝트**야. 현재 실행 환경에 Android SDK/Gradle이 없어 실제 Android 빌드 및 실기기 실행을 검증하지 못했어. APK는 포함되지 않아. JSON/XML/구조와 순수 자바 계산식은 정적 점검 대상이야.

## 구현 범위

| 화면 | 클래스 | 현재 동작 | 미구현/실제 연동 필요 |
|---|---|---|---|
| 로그인/가입 | `LoginActivity` | Firebase 이메일 가입/로그인(설정 시), 명시적 데모 모드, 인증 유지 | 소셜 로그인, 이메일 인증/비번 재설정 |
| 메인 | `MainActivity` | 경로·계획 출발/도착 시간·우천 테스트·빠른 이동 | 실제 기상/교통 실시간 상태 요약 |
| 커스텀 | `CustomActivity` | 출발·도착지, 좌표, 이동수단, 요일, 시간, 우천 계획, 경유지·커피·식사 시간, 저장 | TMAP POI 자동검색, 복수 대중교통 경로 후보 자동 계산 |
| 지도 | `MapActivity` | OSM 개발용 지도, 마커, 보기 토글, 키 연결 시 TMAP **보행**경로 | TMAP Android 지도 SDK, 대중교통/혼합 경로 노선·환승 표시 |
| 실시간 이동 | `TrackingActivity` / `TrackingService` | 사용자 시작형 Fused Location GPS, 포그라운드 알림, 계획 타임라인·계획 도착 시각 | 실제 차량 도착 기반 ETA 재산정, 지연 반영, 구간 자동 완료 판단 |
| 알람 로그 | `AlarmLogActivity` | Room 기반 발송/실패 기록, 중요 강조, 읽음 표시 | 서버 간 로그 동기화(현재 로컬) |

### 실제 동작과 데모/스텁을 구분한 기준

- **구현:** UI 화면 전환, 로컬 루틴 저장, 조건별 *사용자 제공* 이동시간 선택, 계획 시간에 따른 알림, 기기 GPS(사용자 시작), 알림 로그 저장, Firebase 연결 시 인증·Firestore 저장, OSM 지도 마커.
- **조건부 구현:** TMAP AppKey와 좌표가 설정된 **순수 도보** 경로는 TMAP 보행 REST API 결과로 선을 그려. API 결과가 없으면 직선을 '실제 경로'로 오인하게 그리지 않아.
- **시험용 입력:** 우천 체크박스는 실기상 데이터가 아닌 **수동 시나리오**야. 입력한 '일반/우천 예상시간'을 비교하는 규칙만 동작해.
- **미연결:** 실제 강수예보, 서울시 버스·지하철 도착정보, 첫 대중교통과 GPS 보행거리의 결합, 자동 재탐색/도착시간 재계산, 음식점 자동 추천, 공유 킥보드/따릉이 대여 현황.

## 핵심 흐름

```text
LoginActivity
  ├─ Firebase 이메일 로그인/회원가입 (app/google-services.json이 있는 경우)
  └─ 명시적 로컬 데모 진입
       ↓
   활성 출퇴근시간? ── 예 ──→ TrackingActivity (GPS 자동 시작 X)
       │ 아니오
       ↓
   MainActivity
       ├─ CustomActivity ── RoutineStore ── [SharedPreferences / Firestore(선택)]
       │                           └──── ReminderScheduler → ReminderReceiver → Room
       ├─ MapActivity ── [OSM 지도 / TMAP 보행 REST(키 설정 시)]
       ├─ TrackingActivity ── TrackingService → FusedLocationProviderClient
       └─ AlarmLogActivity ── Room
```

**중요:** Android 14 이상에서 사용자 모르게 백그라운드 위치 FGS를 시작하는 것은 제약이 있어. 출퇴근시간에 이동 화면을 열더라도 추적은 사용자가 직접 **위치 추적 시작**을 눌러야 켜져. 백그라운드에서도 계속 추적하려면 서비스는 포그라운드 알림을 유지해야 해.

## 파일 구조 (핵심)

```text
MyCommuteAndroid/
├─ settings.gradle / build.gradle / gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties
├─ local.properties.example          # 실제 비공개 키 설정 예시
├─ firestore.rules                   # 계정별 루틴 읽기/쓰기 규칙
├─ README.md / docs/
└─ app/
   ├─ build.gradle
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml        # 권한/화면/FGS/리시버 등록
      │  ├─ java/com/example/mycommute/
      │  │  ├─ CommuteApplication.java
      │  │  ├─ ui/                     # 6개 화면 + 하단 내비게이션
      │  │  ├─ model/                  # Routine, Waypoint
      │  │  ├─ data/                   # Session, 로컬 저장, Firestore, Room DB
      │  │  ├─ engine/                 # 경로 계획 규칙, ETA/출발 판단 계산
      │  │  ├─ alarms/                 # 예약/푸시 표시/부팅 재예약
      │  │  ├─ location/               # GPS 포그라운드 서비스
      │  │  └─ network/                # TMAP 보행 API, 날씨/대중교통 연결 계약
      │  └─ res/
      │     ├─ layout/                 # activity_*.xml 및 view_bottom_nav.xml
      │     ├─ drawable/               # 알림 아이콘, 카드 배경
      │     └─ values/                 # 문자열/테마/색상
      └─ test/java/                   # 외부 API 필요 없는 순수 계산 테스트
```

## 화면과 문서 매핑

- PDF 1~2쪽: `LoginActivity` → `MainActivity`, 루틴/날씨/상태 요약
- PDF 3쪽: Firebase Authentication (실제 설정 시) / 로컬 데모
- PDF 4쪽: `CustomActivity`, 경유지/편의시설 선택 UI와 루틴 저장
- PDF 5쪽: `MapActivity`, 임시 OSM 지도 + TMAP 보행 REST; **TMAP Android 지도 SDK 자체는 아직 설치되지 않음**
- PDF 6쪽: `TrackingActivity` + `TrackingService` GPS
- PDF 7쪽: `AlarmLogActivity`, Room 기록
- PDF 8쪽: 공통 Java/XML/Intent/JSON/Firebase/위치/알림 기반

## 다음 연동 지점

실서비스에 가깝게 만들려면 `network/ProviderContracts.java` 안의 `WeatherProvider`와 `TransitProvider`를 실제 인증된 공급자 어댑터로 구현하고, `engine/DepartureDecisionEngine.java`와 연결하면 돼. 현재 `DepartureDecisionEngine`은 실제 탑승 예정 차량 도착시각과 정류장까지 걷는 시간·여유시간을 **입력받은 뒤에만** 출발 마감시각을 계산해. 아직 화면에서 호출하지 않으므로 '실시간 동적 알림이 완성됐다'고 해석하면 안 돼.

## 검토 자료 / 공식 문서

- [Firebase Android 설정](https://firebase.google.com/docs/android/setup)
- [Firebase Authentication Android 시작](https://firebase.google.com/docs/auth/android/start)
- [Android 위치 권한](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Android foreground location 서비스 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android 알람 예약 정책](https://developer.android.com/develop/background-work/services/alarms)
- [TMAP API 가이드](https://tmapapi.tmapmobility.com/main_stg.html)
- [서울 열린데이터광장 API 발급/사용](https://data.seoul.go.kr/together/guide/useGuide.do)

외부 API 약관, 비용/한도, 저장 정책은 각 공급자의 이용 약관을 확인해야 해. **TMAP의 Open API 결과를 24시간 이상 저장해서 재사용하는 행위는 정책상 제한될 수 있어.**
