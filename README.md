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
- 사용자가 직접 시작하는 Foreground GPS 추적, 계획 기반 ETA와 진행률, Room 알림 로그
- `WeatherRouteRanker` 조건부 경로 점수 로직과 `DepartureDecisionEngine` 계산 로직

## 미연동 기능

- 실제 기상청 예보·특보 / 날씨별 복수 경로 자동 전환
- 버스·지하철 실시간 도착 및 GPS를 이용한 출발 알림 시각 자동 조정
- 환승 구간별 실시간 진행/ETA, TMAP 지도 SDK 시각화

날씨 데이터는 **미확인**으로 처리하며 임의로 맑음이라고 판단하지 않습니다. 단일 사용자 입력 경로의 시간은 실시간 교통 예측값이 아닙니다. 경유지에 대한 식사·커피 안내는 미구현입니다.

## 검증 및 문서

- `app/src/test/`의 JUnit 단위 테스트는 앱의 실행 화면과 무관하므로 유지합니다.
- 이전 테스트 앱의 API 실험 결과를 설명하는 `docs/HANDOFF.md` 및 `docs/imported/`는 **과거 참고 자료**입니다. 현재 앱 파일 구성·상태는 이 README와 `docs/STATUS.md`를 기준으로 합니다.
- `docs/REQUIREMENTS_CURRENT.md`: 기능 설계. `docs/NEXT_STEPS.md`: 향후 개발 범위.
