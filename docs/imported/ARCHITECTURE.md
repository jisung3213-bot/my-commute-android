# 내부 설계 / 데이터 흐름

## 1. 화면 단위 책임

- **LoginActivity**: Firebase 이메일/비밀번호 인증 또는 사용자가 선택한 명시적 데모. 이미 로그인돼 있으면 루틴의 요일과 출발/예상 소요 시간으로 `MainActivity` 또는 `TrackingActivity` 결정. **추적 서비스 자동 시작 X**.
- **MainActivity**: 등록된 경로 및 사용자 입력 소요시간, 기상 API 미연동 상태, 첫 대중교통 등록값(예측값 아님), 화면 이동.
- **CustomActivity**: 사용자 루틴 / 일반·우천 계획 / 경유지와 커피·식사 시각 / 정류장 식별자 입력 → 검증 → 로컬 저장 → 선택적 Firestore 업로드 → AlarmManager 예약.
- **MapActivity**: 지도 표시, 유효한 좌표의 마커 표시. `도보` 선택과 TMAP 키 유무 확인 후 실제 보행 REST 경로만 Polyline 표시. 대중교통 경로 폴리라인은 없음.
- **TrackingActivity**: 루틴 타임라인 + 계획 이동시간 경과/예상도착 계산 + TrackingService로부터 받은 실제 GPS 위치 표시. GPS 위치로 실시간 환승 판단은 아직 하지 않음.
- **AlarmLogActivity**: Room 로컬 `alarm_logs` 조회 및 미확인 강조/일괄 읽음.

## 2. 저장

- `RoutineStore`: 계정 UID별 `SharedPreferences` JSON. 좌표와 개인 루틴은 로컬로 남아, **로그아웃은 데이터 삭제가 아님**.
- `FirestoreSync`: 로그인 계정 `users/{uid}/routines/primary` 문서의 `payload(JSON 문자열)`과 `updatedAt`. **현재 충돌 해결/실시간 리스너/데이터 삭제는 없음**. 최초 서버 복구는 로컬 루틴 미등록인 경우에만 시도.
- `CommuteDatabase`: `alarm_logs` 테이블. 이메일 비밀번호 자체는 저장하지 않음. 알림 기록은 `accountId`로 필터링. 데모 계정과 실제 계정을 분리.

## 3. 예약 알림

```text
CustomActivity 저장
  ├─ 기존 루틴 PendingIntent 취소
  ├─ RoutineStore 저장 (+ 가능하면 Firestore)
  └─ ReminderScheduler.scheduleCurrent
        ├─ 출발시각 - (사전알림분 + 우천 테스트 추가분)
        └─ [커피/식사] 예약시각
             ↓
        ReminderReceiver
          ├─ NotificationHelper: 시스템 알림 발송 시도
          ├─ AlarmLogRepository: 성공/실패 로그
          └─ 다음 운행 요일의 동일 알림 예약
```

- `BootReceiver`는 재부팅 또는 정확한 알림 권한 부여 후 예약을 복구함.
- 대중교통 도착시간 및 날씨가 연결되지 않았으므로 현재 출발 알림은 **고정 시각 기반**이야.
- 커피/식사 알림은 *경유지에 실제 도착해서* 발생하는 것이 아니라 **사용자 입력 시각**에 발생해.

## 4. 경로 선택의 현재 조건

| 입력 | 현재 판단 |
|---|---|
| 평상시 | 사용자가 입력한 기본 이동수단/예상소요/도보 시간 적용 |
| 시험 우천 ON | 사용자가 지정한 우천 대체수단과 계획값 사용. 기본 자전거가 아닌 대체수단만 인정 |
| 우천 대체수단 자전거/없음 | 대체 경로 필요 메시지 표시. 자동으로 임의 경로 만들지 않음 |
| 날씨 API 미연동 | UI에 미연동 명시. 자동 예보라고 표시하지 않음 |
| 버스/지하철 실시간 미연동 | 실제 도착시간·탑승 가능성 계산 없음 |

## 5. 외부 API 추상화

- `ProviderContracts.WeatherProvider`: 특정 위치/시간에 대한 예보, 강우/자전거 위험 여부 반환. 구현체 없음.
- `ProviderContracts.TransitProvider`: 공급자별 정류장/노선/방향 실시간 도착 응답, `fetchedAtMillis` 메타데이터. 구현체 없음.
- `DepartureDecisionEngine`: 실시간 차량 ETA를 이미 받은 이후 보행시간+탑승 버퍼를 반영할 수 있는 **독립 계산 유틸**. 아직 화면/UI/예약 알림과 연결되지 않음.
- `TmapWalkingClient`: API 키가 존재하고 `도보`로 지정됐을 때만 서버 보행 경로 호출. 키는 APK에 포함되므로 상용에서는 게이트웨이 이관 필요.

## 6. 파일 설계에서 의도적으로 피한 것

- SDK 바이너리 없이 TMAP 지도 SDK가 설치됐다고 주장하지 않음.
- 직선으로 목적지-출발지를 연결해 '실제 이동경로'처럼 표시하지 않음.
- 사용자 개인 위치/GPS 값을 Firebase로 자동 업로드하지 않음.
- Android 제한을 무시하고 알람 수신 시 백그라운드 GPS 서비스를 몰래 실행하지 않음.
- 허용되지 않은 외부 버스/지하철 서비스 호출, 허구의 실시간 도착시간·식당 추천을 생성하지 않음.
