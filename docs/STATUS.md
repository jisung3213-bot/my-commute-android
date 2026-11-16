# 앱 상태 — 2026-11-16

## 반영한 정리

- `seoulmobilitytester` 패키지의 API 테스트 Activity 2개와 테스트용 TMAP API 클라이언트, 관련 레이아웃 2개 삭제.
- Manifest의 개발용 화면 등록과 메인 화면의 진입 버튼 제거.
- Firebase 미설정 우회용 데모 로그인 및 `demo_session` 상태 제거.
- 가상 우천/한파 체크박스·SharedPreferences 접근·앱 실행 경로의 참조 제거. 기상 상태는 미확인으로 취급하고 `WeatherRouteRanker` 엔진은 보존.
- `namespace`, `applicationId`, 리소스/BuildConfig import를 `com.example.mycommute`로 통일하고 Gradle 프로젝트명을 갱신.
- 기존 JUnit 테스트, `TmapWalkingClient`, 경로/알림/GPS 기능은 유지.

## 검증

- 앞선 2026-11-15 통합본에서는 `assembleDebug` 성공 및 16건의 JUnit 테스트 통과가 기록되었음.
- **이번 정리 버전은 별도 Android SDK 환경에서 APK 빌드·기기 동작을 아직 재검증하지 않았음.**
- Firebase 설정 파일이 없으므로 앱 로그인은 실제 Firebase 등록 후 가능.

## 기존 제약

- 날씨, 버스·지하철 실시간 도착, 후보 경로 자동 탐색, 실제 GPS 기반 ETA는 연결 전.
- 앱 ID 변경으로 기존 앱 설치·데이터·Firebase 등록이 자동 계승되지 않음.
- `targetSdk 28`은 기존 요청에 따라 유지되어 최신 상용 배포 요건과 별도로 검토해야 함.


## 2026-11-16 대중교통 경로 API 연결

- TMAP \`/transit/routes\` POST 요청, 최대 3개 경로 후보 JSON 파서 및 단위 테스트 추가.
- 커스텀 화면에 명시적 조회 버튼과 경로 후보 선택 다이얼로그 추가.
- 지도에 선택 경로의 도보/버스/지하철 구간별 실제 응답 좌표를 표시하는 연결 추가.
- 대중교통 AppKey는 \`local.properties\`의 \`TMAP_TRANSIT_APP_KEY\`에서 빌드 시 읽고, Git에 커밋하지 않음.
- API 결과는 영구 저장하지 않으며 15분 TTL의 프로세스 메모리 미리보기로만 사용.
- SDK·AppKey가 필요한 실제 Android 빌드/기기 테스트 및 인증된 실 API 호출은 이 환경에서 미검증.
