# 연동에 필요한 사용자 제공 정보와 계정/인증 가이드

## 반드시 필요 (실제 서버 연결 시)

| 공급자 | 제공해야 하는 정보 | 앱 내 입력/위치 | 현재 구현 |
|---|---|---|---|
| **Firebase** | Firebase 프로젝트 생성, Android 앱 패키지 `com.example.mycommute` 등록, `google-services.json`, Authentication의 **Email/Password 활성화**, Firestore DB 생성, `firestore.rules` 등록 | `app/google-services.json` (절대 채팅에 비밀번호 입력하지 않음) | **연결 시 인증 및 기본 루틴 클라우드 동기화 지원** |
| **TMAP Open API** | 등록한 앱의 **AppKey**, 보행 경로/POI/API 허용 조건과 호출 한도 확인 | 루트 `local.properties`의 `TMAP_APP_KEY=...` | 순수 보행경로 REST 연동; 키가 없으면 지도 마커만 |
| **TMAP Android 지도 SDK** | 앱 등록, 발급키, 배포 방식에 맞는 **정식 Android SDK 바이너리/의존성 가이드** | 추가 모듈 `network`/`ui/MapActivity` 교체 | **미연결**. 현재 개발용 지도는 OSM이야 |
| **기상청** | 기상청 단기예보/초단기예보 인증키 또는 서비스키, 서비스 사용 승인/요청 한도 | 추후 서버 비밀 설정 (`WeatherProvider`) | **미연결**. 우천 체크박스는 시험용 |
| **서울시 버스 실시간 도착** | 서울 열린데이터광장 또는 국토교통부 관련 버스 도착 API 사용 권한·키, 정류장 식별자, 노선/방향 식별자 | 추후 서버 비밀 설정 (`TransitProvider`) | **미연결** |
| **서울시 지하철 실시간 도착** | 서울 열린데이터광장 지하철 도착 API 키, 역 코드/명칭, 노선/상·하행 및 행선지 정보 | 추후 서버 비밀 설정 (`TransitProvider`) | **미연결** |
| **대중교통 혼합 경로 공급자** | 대중교통 출퇴근 길 탐색과 보행·환승 세그먼트를 반환하는 **별도 서비스/계약/키** 및 API 규격 | 향후 route provider 구현 | **미연결**, TMAP 자동차/보행 API만으로 대중교통 환승 일정이 자동 산출되지 않아 |

**사용자가 서비스에서 직접 입력할 데이터:** 출발/도착지, 시각·요일, 일반/우천 이동시간 예측값, 자주 타는 버스·지하철 노선/정류장 ID·방향, 경유지 명칭/좌표, 커피·식사 알림 시각. GPS 권한·알림 권한은 앱 실행 중 Android에서 승인해야 해.

## Firebase 연결 (기기에서 ID·PW 로그인 사용)

1. [Firebase Console](https://console.firebase.google.com/)에서 프로젝트와 Android 앱을 생성해. 패키지명: **`com.example.mycommute`**. 나중에 바꾸려면 `applicationId`도 일치시켜야 해.
2. 콘솔의 `google-services.json` 다운로드 → `app/google-services.json`에 배치해. Gradle은 파일이 존재할 때만 `com.google.gms.google-services` 플러그인을 적용하게 구성됐어.
3. Authentication → Sign-in method → **Email/Password** 사용으로 전환해.
4. Firestore Database를 생성하고 프로젝트 루트의 `firestore.rules`를 Firestore Security Rules에 적용해. 각 로그인 사용자의 UID 경로 `users/{uid}/routines/primary`만 허용해.
5. Android Studio에서 Gradle Sync 후 실행하면 이메일 회원가입·로그인이 활성화돼. 데모 모드는 계정/서버 인증을 통과한 것이 아님을 UI에 표시해.
6. 실제 제품에는 별도로 이메일 인증, 비밀번호 재설정, 탈퇴/데이터 삭제, 개인정보 처리정책, 장애 시 동기화 복구/충돌 처리가 필요해.

**보안:** `google-services.json`의 일반 프로젝트 식별자는 비밀키와는 구분되지만, 프로젝트 설정파일은 공개 저장소에서 관리하지 않도록 `.gitignore`에 포함했어. `TMAP_APP_KEY`처럼 APK에 포함되는 키는 추출될 수 있으므로 **상용 환경에서는 서버 프록시 + 공급자별 제한/인증**이 필요해.

## TMAP 보행경로 키 설정

```properties
# MyCommuteAndroid/local.properties
sdk.dir=<본인_Android_SDK_경로>
TMAP_APP_KEY=<발급받은_앱키>
```

- 보행 모드, 유효한 출발·도착 좌표가 있을 때만 `TmapWalkingClient`에서 공식 보행자 경로 REST를 요청해.
- 다중 경유는 **구간별 출발→경유→도착 보행 경로 호출**로 단순 결합해. 이는 공급자의 대중교통/자동차 '다중 경유지 최적화 API'를 호출하는 방식이 아니야.
- 경로 결과는 실시간 지도 화면 메모리에만 머물고, Firestore에 보행 경로 원본을 저장하지 않아.
- API 오류/미설정이면 경로 선을 숨기고 오류 상태를 표시해. TMAP 지도 SDK 바이너리가 아직 없으므로 OSM으로 임시 표시해.

## 실시간 알림 완성에 필요한 데이터 구조

```json
{
  "boardingStopId": "PROVIDER_STOP_ID",
  "line": "EXAMPLE_LINE",
  "direction": "EXAMPLE_DESTINATION",
  "arrivalEpochMillis": 0,
  "providerObservedEpochMillis": 0,
  "walkToStopMinutes": 0,
  "boardingBufferMinutes": 2,
  "weather": {"rain": null, "validAtMillis": 0}
}
```

위 JSON은 **구현할 공급자 어댑터의 예시 인터페이스**야. `arrivalEpochMillis: 0`은 '미수신'을 뜻하며 도착 예측값이 아니야.

`DepartureDecisionEngine.compute(now, arrival, walk, buffer)`는 실제 `arrival`과 정류장까지 보행시간이 공급된 경우 `arrival - walk - buffer`로 안전 출발 시점을 계산해. 출발지 GPS에서 정류장까지 보행시간은 **보행 경로 계산 결과에 근거해** 구해야 하고, 직선거리를 그대로 시간으로 환산해서는 안 돼. API 데이터의 갱신시각·결측·지연도 확인해야 해.

## 필요한 API를 연결하는 순서

1. **Firebase Authentication + Firestore**: 사용자별 루틴의 실제 저장·인증 확인.
2. **TMAP POI 및 지도 SDK / 도보 경로**: 출발지·도착지·경유지 정확한 좌표 확보. 현재 수동 좌표와 보행 REST만 존재.
3. **기상청 단기예보**: 출발 예정시간에 맞는 예보, 우천/강풍/한파 상태 판단. 테스트 체크박스 제거.
4. **버스·지하철 실시간 도착 API**: 정류장·노선·방향별 정확한 차량 도착 예측값 수신.
5. **대중교통 멀티모달 경로 공급자**: 여러 도보/버스/지하철 세그먼트 후보의 ETA와 환승 시각을 반환해 날씨/사용자 루틴에 따라 비교.
6. **출발 판단 엔진과 알림**: 첫 탑승 차량의 도착 시각 − 도보시간 − 탑승 버퍼 − 기상 영향으로 출발 알림을 재설정. 서버 폴링/FCM 또는 Android 작업 스케줄러와 배터리·할당량·백그라운드 제한을 함께 설계.

## 권한 및 중요한 제약

- **위치**: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`. 현재 앱은 화면에서 사용자가 **위치 추적 시작**을 선택한 경우에만 서비스를 실행해. 백그라운드에서 기상/버스 알림만으로 자동으로 위치 추적을 켤 수 있다는 보장은 없어.
- **알림**: Android 13 이상은 `POST_NOTIFICATIONS` 승인이 필요해. 거부하면 앱 내 기록에 알림 표시 실패가 남을 수 있어.
- **정확한 알람**: Android 12 이상 `SCHEDULE_EXACT_ALARM` 특별 접근이 필요할 수 있어. 거부되면 `setAndAllowWhileIdle`로 완화하여 예약하지만 **정확한 시각을 보장하지 않아**.
- **요금/한도**: TMAP 서비스별 무료 호출 한도와 API 결과 재사용 제한을 반드시 확인해. 버스/지하철/기상 API에도 각기 할당량/인증 조건이 있어.

## 참조 문서

- Firebase 설정: https://firebase.google.com/docs/android/setup
- Firebase 이메일 인증: https://firebase.google.com/docs/auth/android/start
- TMAP 서비스/API: https://tmapapi.tmapmobility.com/main_stg.html
- TMAP 서비스 약관(결과 데이터 저장기간 제약): https://tmapapi.tmapmobility.com/terms.html
- 서울 열린데이터광장 키 발급: https://data.seoul.go.kr/together/guide/useGuide.do
- Android 위치 권한: https://developer.android.com/develop/sensors-and-location/location/permissions
- Android 위치 FGS 시작 제한: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android 알람 정책: https://developer.android.com/develop/background-work/services/alarms
