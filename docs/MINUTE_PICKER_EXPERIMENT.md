# 분 단위 선택 UI 실험

브랜치: `feat/number-picker-inputs` (기준: `main`, 2026-11-23)

## 범위
- 루틴 편집 `activity_custom.xml`의 `editMargin`: 도착 여유시간 **0~180분**
- 설정 `activity_settings.xml`의 다섯 항목:
  - `settingPrepareMinutes`: **1~120분**
  - `settingVehicleMinutes`: **1~30분**
  - `settingWalkSlackMinutes`: **0~20분**
  - `settingDelayMinutes`: **1~60분**
  - `settingDefaultMargin`: **0~120분**
- 기존 목표 도착시각은 `CustomActivity.pickArrival()`의 `TimePickerDialog` 유지

## 작동 방식
1. XML의 숫자 `EditText`를 클릭 가능한 `MaterialButton`으로 교체.
2. 공통 `ui/MinutePicker.java`가 `NumberPicker`를 `AlertDialog`에 띄움.
3. 확인할 때만 버튼의 텍스트와 정수값 태그 갱신; 취소하면 이전 값 유지.
4. 기존 저장 동작은 `MinutePicker.value()`로 정수를 가져와 `Routine.marginMinutes`, `AlarmSettingsStore.setThresholds()` 및 `setDefaultMargin()`에 전달. 기존 DB/Firestore 데이터 형식과 알림 로직은 변경하지 않음.

## 확인할 사항
- 커스텀: 저장된 루틴 열기/방향 변경, 0·180분 선택, 취소 후 값 유지, 저장 후 다시 열었을 때 값 일치.
- 설정: 각 항목의 최소·최대값, 확인·취소, 설정 저장 후 재진입 확인.
- 알림: 시간 임계값 변경 후 예약 재계산 동작 유지.
- 화면: 작은 에뮬레이터에서 휠·확인 버튼이 잘리지 않는지 확인.

정적 확인: XML ID 및 Java 참조 일치, 관련 Java 파일 중괄호 균형 확인. **Gradle 빌드/에뮬레이터 렌더링은 실행하지 않았음.**

## 되돌리기
`main`은 수정하지 않았으므로 브랜치를 변경하면 기존 숫자 입력 UI로 복귀함.

```powershell
git fetch origin
git switch main
git pull origin main
```

다시 실험 브랜치로 가려면 `git switch feat/number-picker-inputs` 실행. 현재 로컬에 커밋되지 않은 변경이 있으면 브랜치 전환 전에 보관해야 함.
