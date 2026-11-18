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

## 전체 자동 검증 버튼 (추가)
- 디버그 빌드에서 설정 > 알림 테스트 모드 ON > 알림 시뮬레이터 > **전체 알림 자동 검증 시작 (한 번 실행)**.
- 실행 후 전체 보고서가 화면에 출력되고 클립보드에 자동 복사됨. '최근 전체 검증 결과 다시 복사' 버튼으로 다시 복사 가능.
- `PASS`: 실제 production 순수 엔진 또는 Android API/DB 호출의 해당 조건을 직접 검사해서 성공함.
- `FAIL`: 단언 실패, 권한 거부, 알림 요청 거부, 로그 누락 등의 검증 실패. 예외도 가능한 범위에서 실패로 기록.
- `NOT_RUN`: 실제 이동/백그라운드/외부 API 등 이 버튼만으로 검증할 수 없는 항목. PASS로 계산하지 않음.
- 10종 시스템 알림은 실제 `NotificationHelper`를 통해 요청되며 `[TEST]` 제목과 `test:` DB 로그로 남음. **notify() 호출 성공은 사용자 화면 수신·팝업 표시 보증이 아님**.
- 검사 내용은 시간 및 주간 일정, 차량 ETA 수명·경계, 보행 여유, 경로 단계 판별, GPS 오류, 지연, 경로 이탈, 목적지 완료, 구간별 중복 방지·재발송 쿨다운, 기기 알림 채널/권한, 10건 발송 요청, Room 쓰기/읽기임.
- 환승·하차 경계 검사는 순수 거리·지속시간 조건만 평가하며 실제 `TravelAlertMonitor` 통합 실행은 하지 않음. 날씨 기상 예보 발송 수신기도 미실행.
- 검증용 ledger 키는 실행별 별도 테스트 ID를 사용하고 검사 종료 후 제거. 기존 사용자 경로, Firebase 동기화 및 실제 API를 변경하지 않음.
- **현재 자동화는 실제 Android 빌드/기기 실행 전 코드 반영 상태**이며, 결과 값은 사용자의 Android Studio 실행 후 보고서로 확인해야 함.
