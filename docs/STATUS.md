# 통합 상태 — 2026-11-15

## 기준

사용자 제공 ZIP을 현재 프로젝트에 실제 통합했다. 기존 앱 ID와 Android Studio/SDK/AGP/Gradle/JDK 버전은 유지했다.
SDK 34에서 호환되지 않는 AndroidX Core 1.15.0을 1.13.1로 조정했고, 선택적 Firebase 설정에 Google Services 플러그인 4.4.2를 추가했다.
기존 API 테스트 Java 파일과 테스트 화면은 보존하고 메인에서 연결했다.
작업 전 Git 소스는 별도 `materials/sources/backups/before_zip_adoption_c0106db.zip`에 보존했다.

## 검증

- `assembleDebug`: 성공, APK 생성.
- 실제 테스트 클래스 3종/JUnit 회귀 테스트 **16개 통과** (`scripts/test-core.ps1`).
- 원래 `testDebugUnitTest`는 Windows 한글 경로의 Gradle worker에서 ClassNotFoundException으로 실행 실패했다. 이를 테스트 통과로 보고하지 않는다. 같은 컴파일 결과를 직접 JUnit으로 실행하여 16개 사례를 검증했다.
- Lint: targetSdk 28 유지에 따른 ExpiredTargetSdkVersion 오류 1건 및 경고 187건. 새 소스의 NewApi 스타일 오류는 수정했다. 검사 비활성화/전체 baseline 처리는 하지 않았다.
- 기상 API/특보, 실제 교통 도착, Firebase 서버, TMAP 실호출은 이번 작업에서 미검증.
- 기존 API 34 가상기기 `U_Pixel_2_`의 읽기 전용 임시 세션에서 APK 설치 Success, LoginActivity 시작 Status: ok, 프로세스 유지 및 crash buffer 비어 있음 확인. 기기 설정/스냅샷을 저장하지 않고 종료했다. UI 전체 흐름과 실제 기기 알림/GPS 검증을 대신하지 않는다.

## 한계

로그 날짜 필터/상세, Firestore 동기화 상태 UI/충돌 해결, GPS 경로 매칭, 동적 ETA 등은 미구현이다.
수동 기상 시험 및 사용자 입력 계획과 실제 외부 데이터를 구분해서 표시한다.
날씨 가중치는 개발용 초기 정책이며 실측 성능이나 최적성을 검증한 값이 아니다.
