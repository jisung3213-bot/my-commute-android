# 나만의 출퇴근 — ZIP 기반 통합본

사용자 제공 `MyCommuteAndroid_Source.zip`의 6개 화면을 기존 프로젝트에 통합한 개발 버전입니다.
기존 TMAP 대중교통·POI·보행 및 따릉이 테스트는 메인의 **개발용 API 연결 확인**에서 사용할 수 있습니다.

## 열기와 실행

Android Studio에서 이 README와 settings.gradle이 있는 **MyCommuteAndroid 폴더**를 엽니다.
기존 환경을 유지합니다: **AGP 8.2.0 / Gradle 8.2 / JDK 17 / compileSdk 34 / minSdk 26 / targetSdk 28**.
앱 ID도 `com.example.seoulmobilitytester`로 유지했습니다. 앱 버전은 0.2.0입니다.
SDK 35나 새 Android Studio를 설치할 필요가 없습니다.

1. Gradle JDK를 기존 JDK 17로 설정하고 Sync합니다.
2. app 실행 구성으로 실행합니다.
3. Firebase 미설정 상태에서는 **데모 모드로 시작**을 선택합니다.
4. 메인에서 출근/등교 또는 퇴근/하교 루틴을 선택하고 등록합니다.
5. 출발·도착 장소, 목표 도착시간, 이동시간 추정, 도보시간, 여유시간, 요일을 직접 입력합니다. 개인 경로와 시각은 미리 채우지 않습니다.
6. 요일은 **목표 도착일** 기준입니다. 예를 들어 화요일 00:35 도착은 월요일 밤에 출발할 수 있습니다.
7. 지도는 좌표 입력 시 마커를 표시하며, 도보 경로는 TMAP 키가 있을 때 조회합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\scripts\test-core.ps1
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (Git 제외).
Windows 한글 경로에서 Gradle 8.2 테스트 실행기가 클래스를 찾지 못하는 경우가 확인되어,
`test-core.ps1`은 Gradle로 실제 테스트 코드를 컴파일한 후 같은 JUnit 테스트를 직접 실행합니다.
테스트를 무시하거나 기본값으로 바꿔 통과시키지 않습니다.

## 이번 반영

- 로그인, 메인, 커스텀, 지도, 이동, 로그 화면 통합.
- 출근/퇴근 루틴 별도 로컬 저장 및 계정별 Firestore 문서 분리.
- 목표 도착시간 − 이동시간 − 여유시간으로 출발 권장시각 계산. 알림과 메인에서 같은 계산 사용.
- 자정 경계와 첫 차량에 걸어갈 시간이 부족한 경우의 판정 수정.
- 비/한파 조건별 후보 점수와 자전거 제외 규칙 추가. 점수와 실제 이동시간은 분리.
- 식사/커피는 추후 확장으로 표시하고 실제 입력·알림은 제외.
- API 26~30 정확 알람 분기, 계정·루틴별 알림 식별자, 시스템 시각 변경 재예약 처리.
- 알림 차단 상태 확인, 실제 이동 시작 기준 계획 진행, 시작/종료 로그.
- 원본 ZIP의 스타일 빌드 오류 및 API 26 테마 호환성 수정.

## 아직 미완성인 핵심

**기상 체크박스는 수동 시험이며 실제 예보가 아닙니다.** 가중치 엔진은 복수 후보를 평가할 수 있지만,
현재 앱에는 사용자가 입력한 단일 계획만 연결되어 있습니다. 대안 경로를 지어내지 않습니다.
우천/한파의 자전거 또는 구간을 알 수 없는 혼합 경로는 대체 경로 필요로 안내하고 출발 알림을 예약하지 않습니다.

실제 기상청/특보, 대중교통 후보 선택과 정류장 ID 매핑, 차량 도착+GPS 결합 알림, GPS 기반 동적 ETA는 다음 단계입니다.
현재 이동 시간과 진행률은 **실제 이동 시작 후 경과시간에 따른 계획 추정**입니다. GPS로 구간 완료나 지연을 판정하지 않습니다.
지도는 ZIP의 OSM 개발용 화면이며 TMAP Android 지도 SDK와 이동 화면 지도는 아직 미연결입니다.
알림 로그는 최신 200개 목록·중요 강조·일괄 읽음까지 제공하며 날짜 필터/상세/개별 읽음은 후속입니다.

## 키와 Firebase

- 현재 패키지명은 **com.example.seoulmobilitytester**입니다. 원본 ZIP 문서의 com.example.mycommute와 혼동하지 않습니다.
- Firebase Android 앱을 현재 패키지명으로 등록하고 `app/google-services.json`을 직접 넣습니다.
- 이메일/비밀번호 Authentication을 활성화하고 Firestore에 `firestore.rules`를 적용합니다.
- TMAP 보행 키는 기존 `local.properties`에 `TMAP_APP_KEY=...`를 추가합니다. SDK 경로를 덮어쓰지 않습니다.
- 개발용 원본 API 테스터는 실행 화면에서 키를 입력합니다.
- 키/설정 JSON/서명 파일은 Git 제외입니다. 빌드에 넣은 TMAP 키가 APK에서 비밀로 보호되는 것은 아닙니다.

## 개발 기록

현재 요구사항: `docs/REQUIREMENTS_CURRENT.md`, 검증 결과: `docs/STATUS.md`, 다음 단계: `docs/NEXT_STEPS.md`.
`docs/imported/`는 전달 ZIP의 **수정 전 참고 문서**입니다. 현 상태는 이 README와 STATUS가 우선합니다.
GitHub: https://github.com/jisung3213-bot/my-commute-android

targetSdk 28은 사용자의 기존 설정 유지 요청에 따른 것입니다. 상용 배포 완료 상태가 아닙니다.
