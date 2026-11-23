# 분 입력 UI 변경 이력 — PR #2 병합 완료

**상태:** 2026-11-23 [PR #2](https://github.com/jisung3213-bot/my-commute-android/pull/2)로 `feat/number-picker-inputs`를 **`main`에 병합**했습니다. 실험 브랜치만의 기능이 아닌 현재 기본 UI입니다.

## 적용 항목

| 위치 | 필드 | 범위 |
| --- | --- | --- |
| 루틴 편집 `activity_custom.xml` | 도착 여유시간 `editMargin` | 0~180분 |
| 설정 `activity_settings.xml` | 출발 준비 `settingPrepareMinutes` | 1~120분 |
| 설정 | 차량 도착 임박 `settingVehicleMinutes` | 1~30분 |
| 설정 | 승차지 출발 여유 `settingWalkSlackMinutes` | 0~20분 |
| 설정 | 도착 지연 `settingDelayMinutes` | 1~60분 |
| 설정 | 새 루틴 기본 여유 `settingDefaultMargin` | 0~120분 |

목표 도착시각은 기존 `CustomActivity.pickArrival()`의 **TimePickerDialog**를 유지합니다.

`ui/MinutePicker.java`는 MaterialButton을 누르면 `AlertDialog`에 `NumberPicker` 휠을 표시합니다. 확인하면 버튼의 표시값과 정수 태그를 업데이트하고, 취소하면 이전 값을 유지합니다. **기존 저장 버튼을 눌러야** `Routine.marginMinutes`, `AlarmSettingsStore`에 실제 저장됩니다. Firestore JSON과 알림/경로 추천 알고리즘의 데이터 타입은 변경하지 않았습니다.

## 검증 범위

- 분 입력 6개 ID와 Java 참조가 연결되어 있고, 기존 설정값의 최소·최대 범위를 유지하는지 코드에서 확인했습니다.
- 사용자가 실험 브랜치의 UI를 확인해 만족한다고 알리고 `main` 병합을 요청했습니다.
- **이 기능 병합 이후 전체 Gradle 빌드·에뮬레이터·실기기 회귀 검증은 이 문서의 근거 자료에 없습니다.** 과거 `STATUS.md`의 빌드 결과와 구분합니다.

## 복귀 정보

`main`은 현재 NumberPicker UI를 포함합니다. `git switch main`만으로는 옛 숫자 직접 입력 UI로 돌아가지 않습니다. 변경 전 코드 기준은 PR #2의 base인 [`8f88596`](https://github.com/jisung3213-bot/my-commute-android/commit/8f88596b4fae03e953d710a396446b6a076b2ea5)입니다. 현재 코드를 유지한 채 되돌리려면 해당 PR의 변경 부분만 검토하여 역적용해야 합니다.
