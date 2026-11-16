# 나만의 출퇴근 (MyCommuteAndroid)

Java 17 + XML 기반 Android 앱. 개발용 API 실험 화면, 데모 로그인, 수동 가상 날씨 시나리오는 제거했습니다. 실제 통근 기능에 필요한 로직과 JUnit 검증은 유지합니다.

## 개발 환경

- Android Studio / AGP 8.2.0 / Gradle 8.2 / JDK 17
- `compileSdk 34`, `minSdk 26`, `targetSdk 28` (기존 설정 유지; 최신 배포 기준 미충족)
- `namespace` 및 `applicationId`: `com.example.mycommute`
- 앱 진입: Firebase 이메일 로그인 → 메인 또는 현재 활성화된 이동 화면

## 실행 전 필수 설정

1. Android Studio에서 저장소 루트(`settings.gradle`이 있는 폴더)를 엽니다.
2. Firebase Console에 Android 앱 `com.example.mycommute`를 등록하고 `app/google-services.json`을 배치합니다. **Authentication > 이메일/비밀번호**를 활성화하고 `firestore.rules`를 적용합니다.
3. 선택적으로 `local.properties`에 `TMAP_APP_KEY=...`를 지정하면 지도 화면에서 도보 경로 REST 조회를 사용할 수 있습니다. API 키는 저장소에 커밋하지 않습니다.
4. JDK 17로 Gradle 동기화 및 빌드합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\scripts\test-core.ps1
```

**주의:** Firebase 설정이 없으면 로그인/회원가입이 비활성화되며 데모 우회 경로는 없습니다. 패키지 ID 변경으로 기존 `com.example.seoulmobilitytester` 앱 설치와 저장 데이터는 자동 이전되지 않습니다. Firebase도 새 패키지로 앱 등록이 필요합니다.

## 현재 제공 기능

- Firebase 이메일 로그인/회원가입, 계정별 출근/퇴근 루틴 로컬 저장 및 Firestore 동기화 시도
- 루틴과 경유지 입력, 사용자 지정 이동 예상시간 기반 출발 알림
- OSM 지도/마커 및 TMAP 보행 경로 조회(키 연결 시)
- 사용자가 직접 시작하는 Foreground GPS 추적, GPS-첫 탑승 도착정보 기반의 보수적인 탑승 여유 추정, Room 알림 로그 (기존 수동 계획 루틴의 ETA 별도)
- `WeatherRouteRanker` 조건부 경로 점수 로직과 `DepartureDecisionEngine` 계산 로직

## 미연동 기능

- 실제 기상청 예보·특보 / 날씨별 복수 경로 자동 전환
- 첫 차량 외 환승 구간의 실시간 도착 연계, 실제 보행경로 기반 탑승 판단, GPS를 이용한 출발 알림 시각 자동 조정
- 환승 구간별 실시간 진행/ETA, TMAP 지도 SDK 시각화

날씨 데이터는 **미확인**으로 처리하며 임의로 맑음이라고 판단하지 않습니다. 단일 사용자 입력 경로의 시간은 실시간 교통 예측값이 아닙니다. 경유지에 대한 식사·커피 안내는 미구현입니다.

## 검증 및 문서

- `app/src/test/`의 JUnit 단위 테스트는 앱의 실행 화면과 무관하므로 유지합니다.
- 이전 테스트 앱의 API 실험 결과를 설명하는 `docs/HANDOFF.md` 및 `docs/imported/`는 **과거 참고 자료**입니다. 현재 앱 파일 구성·상태는 이 README와 `docs/STATUS.md`를 기준으로 합니다.
- `docs/REQUIREMENTS_CURRENT.md`: 기능 설계. `docs/NEXT_STEPS.md`: 향후 개발 범위.

## TMAP Raster 지도 SDK 로컬 설치

TMAP SDK 파일은 저장소에 포함되지 않습니다. **Raster Map SDK (Android)** 를 공식 페이지에서 내려받고, 제공된 `.jar` 또는 `.aar`와 필요한 네이티브 라이브러리/종속성을 개발자 PC의 `app/libs/` 또는 안내된 위치에 추가하세요. JAR/AAR의 버전에 따라 메서드가 다를 수 있으니 공식 샘플과 맞는 버전을 사용하세요.

- 공식 SDK 안내: https://tmapapi.tmapmobility.com/main.html#android/sample/androidSample.sdk_download
- `local.properties` : `TMAP_APP_KEY=발급받은키` (Git에 올리지 않음)
- `app/build.gradle` : `implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])`
- Gradle Sync 후 앱 다시 실행. SDK 파일과 키가 모두 감지되면 `MapActivity`가 **TMAP Raster 지도**를 생성합니다.
- SDK가 없거나 초기화에 실패하면 **OSM 임시 지도**가 표시되며 화면에 원인을 안내합니다. 이 경우 TMAP이 실제로 연결된 것이 아닙니다.
- TMAP 지도는 `TmapRasterMapAdapter`의 `com.skt.Tmap.TMapView`를 통해 초기화하고, 기존 `TmapWalkingClient`의 보행 경로 좌표를 SDK 폴리라인에 그립니다.
- *주의:* 본 변경은 Android SDK가 없는 환경에서 작성되어 실제 TMAP 배포 바이너리와의 호환성·APK 실행은 아직 검증하지 못했습니다. 최신 Vector SDK는 Raster SDK와 별개이므로 이 어댑터에 섞으면 안 됩니다.


## TMAP 대중교통 경로탐색 (신규)

1. SK OPEN API의 **대중교통 경로탐색 상품**을 신청하여 해당 API를 호출할 수 있는 AppKey를 준비합니다.
2. 프로젝트 최상위 \`local.properties\`의 기존 \`sdk.dir\`, \`TMAP_APP_KEY\`는 유지하고 다음 줄을 추가합니다.

\`\`\`properties
TMAP_TRANSIT_APP_KEY=여기에_대중교통_API_앱키
\`\`\`

3. Android Studio **Gradle Sync** 후 다시 빌드/실행합니다. 비밀키를 GitHub에 올리지 마세요.
4. Firebase 로그인 → **커스텀** → 출발지/도착지 이름, 위도/경도, 이동시간, 목표 도착시간, 요일을 입력하고 **루틴 저장**을 누릅니다.
5. **저장된 출발·도착지로 대중교통 경로 검색** 버튼을 누르면 TMAP \`POST /transit/routes\` API를 최대 3개 후보로 한 번 호출합니다.
6. 후보 선택 시 지도 화면으로 이동하여 응답의 **도보(회색)·버스(파랑)·지하철(빨강)** 구간 선을 표시합니다. 경로 선이 없는 구간은 임의로 연결하지 않습니다.

**제약:** 이 단계는 사용자가 버튼을 누를 때만 경로를 조회합니다. 무료체험 호출량을 소모하므로 화면 진입·리렌더링 시 자동으로 요청하지 않습니다. 선택 경로는 **15분간 앱 프로세스의 메모리에만** 남고 Firestore에 저장하지 않습니다. 앱을 종료하거나 루틴을 변경하면 다시 검색해야 합니다. 실제 버스·지하철의 실시간 도착 정보와 GPS 기반 ETA는 아직 연동하지 않았습니다.

**보안:** \`local.properties\`는 Git에서 제외되지만, Gradle의 \`BuildConfig\` 필드에 넣은 API 키는 **빌드된 APK 내부에 포함**됩니다. 이 구성은 개발·시연용이며, 상용 배포 시에는 서버 프록시 및 호출 제한·인증 정책이 필요합니다.


## 출퇴근 커스텀 화면 개편: 장소 선택과 경로 카드

- **장소 검색:** \`PlaceSearchActivity\` + \`TmapPoiClient\` 가 검색어를 TMAP POI API로 조회해 최대 10개 장소를 표시합니다. 사용자는 좌표를 입력하지 않고 검색 결과를 선택합니다. 호출에는 기존 \`TMAP_APP_KEY\`를 사용합니다.
- **경로 비교:** 출발·도착 장소, 목표 도착 시각, 요일, 여유시간을 설정한 뒤 **대중교통 경로 검색하기**를 누릅니다. \`TMAP_TRANSIT_APP_KEY\`를 이용해 최대 3개 후보를 조회하고 이동시간·도보시간·환승횟수·구간을 화면에 표시합니다.
- **경로 선택:** 후보 카드를 탭해 선택하고 **선택 경로 지도에서 보기**를 눌러 지도에 임시 경로를 표시합니다. **선택한 경로로 출퇴근 루틴 저장**을 누르기 전에는 루틴 데이터가 변경되지 않습니다.
- **기상정보 토글:** 선택적으로 \`KMA_SERVICE_KEY\`가 있으면 기상청 단기예보 API로 목표 도착시간대의 출발지 격자 예보를 조회해 강수·저온 기반 경로 점수에 적용합니다. 조회 실패·미설정·예보 범위 밖일 때는 **기상 미확인, 기본 이동시간순**이라고 명시합니다. 기상특보와 실외 대기 시간은 아직 연결되지 않았습니다.
- **장소 추가:** 추가한 경유지·편의시설은 **지도 마커만** 표시합니다. 현재 TMAP 대중교통 API 요청은 출발지→도착지로만 계산하므로 경유지를 필수 이동 구간으로 합성하지 않습니다.
- **알림 주의:** 새 루틴은 \`apiBased=true\` 및 수동 예상시간 없이 저장되며, 기존 사용자가 입력했던 분 단위 계획과 혼동하지 않도록 **자동 출발 알림은 의도적으로 예약하지 않습니다.** 실시간 교통 기반 재조회·알림 예약은 후속 연동 대상입니다.
- TMAP 조회 경로 전체는 영구 저장하지 않고 프로세스 메모리에 15분간 보관합니다. 저장하는 것은 장소·좌표·목표시각·요일·추천 설정입니다.
- \`local.properties\`에 필요시 다음 줄을 추가합니다 (공공데이터포털 서비스키는 **디코딩 원문 형태** 권장).

\`\`\`properties
TMAP_APP_KEY=TMAP_POI_MAP_KEY
TMAP_TRANSIT_APP_KEY=TMAP_TRANSIT_KEY
KMA_SERVICE_KEY=OPTIONAL_DECODED_KMA_SERVICE_KEY
\`\`\`

**개발 검증 한계:** Android SDK·발급된 API 키·TMAP Raster 배포 파일이 없는 작업 환경이므로 실제 APK 빌드, 실 API 결과, 지도 SDK 1.77 바이너리 호환성은 확인되지 않았습니다. 배포용 앱에서는 API 키를 APK에 직접 포함하지 않는 별도 서버 프록시가 필요합니다.


## 첫 탑승 구간 실시간 버스/지하철 도착정보

커스텀 화면에서 TMAP 대중교통 경로 후보 중 하나를 **선택**한 후 **실시간 첫 탑승 도착정보 조회** 버튼을 누르면, 선택 경로의 첫 버스/지하철 구간을 찾아 도착정보를 조회합니다. 임시 경로가 유효하면 이동 화면에서도 수동 새로고침할 수 있습니다. 30초 간격 제한이며 백그라운드 반복 호출은 하지 않습니다.

\`\`\`properties
# 프로젝트 루트 local.properties (Git에 커밋하지 마세요)
SEOUL_SUBWAY_API_KEY=발급받은_서울시_실시간지하철_인증키
TAGO_BUS_SERVICE_KEY=발급받은_공공데이터포털_디코딩_인증키
\`\`\`

- **서울 지하철:** \`realtimeStationArrival\` REST/JSON을 사용합니다. 역명과 노선 ID를 확인하고, TMAP에서 다음 역 이름이 확보된 경우에는 서울시 응답의 행선지 텍스트에도 해당 역명이 명확하게 포함된 열차만 표시합니다. **행선지와 다음역의 연결은 제공자 데이터만으로 확실하지 않은 경우가 많아서** 실제 열차가 있어도 \`확인 불가\`라고 표시될 수 있습니다. 반대방향 열차를 추정으로 표시하지 않습니다. 서울시 공식 문서의 HTTP 엔드포인트를 위해 해당 도메인만 cleartext 허용했습니다.
- **TAGO 버스:** TMAP 승차 정류장 좌표로 **TAGO 버스정류소정보** \`getCrdntPrxmtSttnList\`를 호출해 \`citycode\`·\`nodeid\`를 조회합니다. 정류장 이름과 거리(70m 이내) 기준이 충족되고, 근처 동일 이름의 후보가 모호하지 않을 때만 **TAGO 버스도착정보** \`getSttnAcctoArvlPrearngeInfoList\`를 호출해 같은 노선번호의 도착예정시간을 표시합니다. 별도의 **TAGO 버스정류소정보 API 활용신청**이 추가로 필요할 수 있습니다.
- API 반환 시간은 조회 시점의 값이며 일정한 간격으로 자동 갱신되지 않습니다. **전체 여행 ETA/출발 알림에 자동 반영하지 않습니다.** 추가적인 차량 방향·급행 검증, 승차역 정규화, 실시간 자동 리프레시/알림은 추후 개선 대상입니다.
- 두 API 키는 개발 단계에서 \`BuildConfig\`에 들어가므로 APK를 역분석하면 추출할 수 있습니다. 상용 서비스에서는 백엔드 프록시와 요청 제한으로 관리해야 합니다.
- Android SDK·실제 API 키가 없어 이 커밋에서 APK 빌드와 인증된 API 호출은 확인하지 못했습니다. \`ArrivalMatcherTest\`에 정류장 중복 매칭 거부 및 버스 노선/역방향 미확인 테스트를 추가했습니다.

참고: https://data.seoul.go.kr/dataList/OA-12764/F/1/datasetView.do · https://www.data.go.kr/data/15096280/standard.do · https://www.data.go.kr/data/15098530/openapi.do


## GPS × 첫 탑승 도착정보: 실시간 탑승 여유 추정

- 저장된 API 기반 출퇴근 루틴에서도 **위치 추적 시작**이 가능하도록 개선했습니다. 위치 추적은 사용자가 명시적으로 시작하고 중지하는 포그라운드 서비스만 사용합니다.
- **실시간 이동** 화면에서 TMAP 경로를 검색·선택한 상태로 **실시간 도착정보 새로고침**을 누르면, 첫 BUS/SUBWAY 구간의 차량 도착정보와 최근 GPS 위치를 결합해 **정류장까지 거리(직선), 근사 보행시간, 남은 차량 도착시간, 탑승 여유 추정**을 표시합니다.
- 도보 거리는 **직선거리 × 1.45**, 속도 **1.15 m/s**, 탑승 여유 **1~2분**을 이용한 근사값입니다. 실제 보행길이나 정류장 출입구를 탐색한 값이 아니며 **탑승 가능 보증·실시간 전구간 ETA가 아닙니다.**
- GPS 위치가 **90초 이상 오래됐거나 정확도 75m 초과/미확인**, 차량정보가 **120초 이상 오래됐거나**, 정류장 좌표/차량정보가 없다면 **탑승 판단 보류**로 표시합니다. 예상 차량 도착 시각을 지나도 새 정보를 자동 추측하지 않고 재조회가 필요합니다.
- API 기반 루틴에 대해서는 기존 사용자 수동 계획에 의존한 전체 이동 ETA나 자동 출발 알림을 계속 **예약하지 않습니다**. 이번 단계는 화면에서 실시간 정보를 확인하는 것이며 자동 알림/정확한 TMAP 보행 경로/백그라운드 주기 조회는 다음 개발 범위입니다.
- GPS는 \`TrackingService\`가 사용자가 시작할 때만 주기적으로 수집하며, \`BoardingFeasibilityEngine\`은 안드로이드와 분리된 단위 테스트를 추가했습니다.
