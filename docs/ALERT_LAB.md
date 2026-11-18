# 디버그 전용 알림 시뮬레이터

## 구조
- 실제 APK(release): `AlertLabMode.enabled()`는 항상 false이며 디버그 Activity는 포함되지 않음.
- 개발 APK(debug): 설정 → '알림 테스트 모드'를 켜고 '알림 시뮬레이터 열기'로 실행.
- 테스트 모드 전환 시 기존 예약 알림 취소 및 위치추적 종료. 실제 모드로 전환 시 예약 재등록.
- 테스트 데이터는 실 API·실 GPS·Firestore·사용자 루틴을 변경하지 않음.
- 알림 출력은 실제 `NotificationHelper.show`, 기록은 `AlarmLogRepository.write` 사용. 테스트 제목/로그에는 `[TEST]` 또는 `test:` 접두사를 붙임.
- 시뮬레이터의 가상 시각은 화면상의 fixture 시간에만 적용됨. 시스템 시계·AlarmManager는 변경하지 않음.
- 10종 일괄 검사는 모의 정상·경계 실패·정보 불확실 fixture에 대한 순수 조건 확인. 세부 수치 입력은 단일 실행의 일부 조건에만 반영. 환승·하차·도착 시 실제 StagePositionMatcher나 원래 TravelAlertMonitor 실행 경로 전체를 통과하지는 않음.
- 테스트 모드에서 실제 예약 발송 및 TrackingService 시작을 차단. 앱을 비정상 종료하고 재시작해도 debug 전용 로컬 플래그가 남을 수 있으므로 테스트 후 반드시 실사용 모드로 복귀.

## 제거 방법
1. `app/src/debug/` 디렉터리 전체 삭제
2. `AlertLabMode.java` 삭제
3. `SettingsActivity`, `ReminderScheduler`, `ReminderReceiver`, `TrackingService`의 `AlertLabMode` 참조 제거
4. 설정의 debug 진입 UI 제거
5. 이 문서 삭제

## 실제 시스템 통합 검증 (시뮬레이터가 대체하지 못함)
- 개발 APK 빌드/JUnit, 알림 권한 허용·거부/알림 클릭/Room 저장
- 실제 Android 정확한 예약, 날짜·요일 경계, 재부팅·절전
- 실제 GPS 경로·위치 정확도 및 TMAP/서울시/TAGO/KMA API 유효 응답
- 반복 수신·차량 승계·멀티 루틴의 중복 억제와 사용자가 추적을 종료했을 때의 동작

테스트 결과가 아닌 구현 명세. 최신 Android 빌드와 기기 실험은 아직 수행하지 않음.
