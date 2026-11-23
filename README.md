# 나만의 출퇴근 (MyCommuteAndroid)

**반복되는 출근·퇴근 경로를 저장하고, 목표 도착시각·날씨·대중교통·현재 위치를 결합해 출발과 이동을 안내하는 Android 앱**입니다. 모바일프로그래밍 팀프로젝트(창업형 앱 제작)의 시연용 파일럿입니다. 실제 운행을 제어하거나 탑승·도착을 보장하는 서비스는 아닙니다.

## 핵심 기능

| 기능 | 현재 구현 |
| --- | --- |
| 사용자 관리 | Firebase Authentication 이메일 회원가입·로그인, 계정별 루틴·설정 동기화(Firestore) |
| 루틴 | 출근·퇴근 방향별 여러 경로, 기본 경로, 도착시각, 반복 요일, 경유지 최대 2곳 |
| 장소·경로 | TMAP POI 검색 및 대중교통 경로 후보 조회, 사용자 선택 경로 저장 |
| 추천 | 이동시간·도보·환승·강수·기온·설정 여유시간의 **규칙 기반 점수**로 1개 우선 추천; 나머지 접기 |
| 지도·이동 | TMAP Raster SDK(별도 설치) 또는 OSM 지도, 경로 약도, 위치 추정, 제공되는 버스·지하철 실시간 도착정보 |
| 출발·알림 | 출발시간 계산, 강수·기온에 따른 최대 15분 출발 앞당김, Android 예약·이동 중 조건부 알림 10종 |
| 설정·기록 | 자동 위치 확인, 알림별 기준, 12/24시간제, Room 기반 알림 기록 |
| 입력 UX | 도착시각은 TimePicker, 여유·알림 기준 **6개 분 항목**은 NumberPicker 선택창 |

**제약:** 공식 한파주의보·경보 API와 전 구간 실시간 도착 확정은 미구현입니다. 지하 구간 GPS, 제공기관의 ETA 누락, 절전 상태의 알림은 제한될 수 있습니다. API 응답값이 없으면 시간을 만들어 표시하지 않습니다.

## 실행 방법

1. **Android Studio + JDK 17**로 루트의 `settings.gradle`을 엽니다. AGP 8.2.0 / Gradle 8.2, `compileSdk 34`, `minSdk 26`, `targetSdk 28`입니다.
2. Firebase 프로젝트의 `google-services.json`을 `app/`에 넣고 Authentication 이메일/비밀번호 공급자를 활성화합니다. 필요한 경우 Firestore 규칙을 **Firebase 콘솔에서 별도 배포**합니다.
3. `local.properties`의 `sdk.dir`을 유지한 채 [설정 예시](local.properties.example)에 따라 TMAP·기상청·서울시·TAGO 키를 등록합니다. 서비스별 이용 권한/승인을 확인합니다.
4. TMAP Raster SDK가 있으면 `app/libs/`에 공급사 바이너리를 별도 설치합니다. 없으면 지도 화면은 OSM 대체 표시를 시도합니다.
5. Android Studio에서 Sync 후 `app`을 Run합니다. **루틴 → 장소 검색 → 목표 도착시각 및 여유 설정 → 경로 검색 → 경로 선택·기본 경로 지정** 순서로 사용합니다.

개발용 파일럿은 `local.properties`, `google-services.json`, SDK 바이너리 없이 모든 연동이 동작하지 않습니다. 이 파일들은 저장소에 포함하지 않습니다. **BuildConfig로 앱에 주입되는 API 키는 APK에서 추출될 수 있으므로 상용 배포 방식이 아닙니다.**

## 화면·코드 구성

```text
app/src/main/
  AndroidManifest.xml         Activity · Service · Receiver · 권한
  java/com/example/mycommute/
    ui/                       Activity 화면 · Navigation · MinutePicker · 경로 약도 · 지도
    model/                    Routine · TransitRoute · TransitArrival · Waypoint
    network/                  TMAP · KMA · 서울시 · TAGO API 클라이언트
    engine/                   경로 추천 · 이동시간/위치/승차 조건 계산
    data/                     루틴·캐시·Firestore·Room·출발 예보 사전조회
    alarms/                   AlarmManager 예약 · Receiver · 알림 메시지/중복 방지
    location/                 Foreground TrackingService
  res/layout/                activity_*.xml · item_route_result.xml · view_bottom_nav.xml
```

기본 화면은 Activity의 `setContentView()`, 하단 메뉴는 XML `<include>`, **경로 후보 카드는 `item_route_result.xml` Inflate**, 지도는 FrameLayout에 SDK View를 추가하는 방식입니다. [파일·상호작용 상세](docs/ARCHITECTURE_CURRENT.md)

## 검증 상태와 알려진 한계

- 2026-11-23까지의 **이전 실행 기록**에는 Debug/Release 빌드, Lint 오류 0건, 일부 Android 14 격리 시연/기상 보정 확인이 있습니다. [검증 범위](docs/STATUS.md)
- **2026-11-23 PR #2로 병합된 NumberPicker 변경은 코드·ID 연결을 확인했지만, 그 이후 빌드와 실기기 재검증 기록은 없습니다.** 과거 빌드 성공을 최신 커밋의 실행 성공으로 간주하지 않습니다.
- 정상 Firebase 신규 가입·다기기 동기화, 실기기 잠금·절전 중 장시간 GPS/알림, 모든 노선의 실시간 도착정보는 추가 검증 대상입니다.
- 현재 `main`에는 과거 알림/API/GPS/추천 **디버그 실험 화면과 JUnit 테스트 소스가 없습니다**. 기존 실험 기록은 역사 문서로만 보관합니다.
- Android `targetSdk 28`, APK 내 API 키 등은 학기 프로젝트 파일럿의 한계이며 최신 스토어 출시 준비가 끝났다는 의미가 아닙니다.

## 과제 제출과 발표

첨부된 **모바일프로그래밍 팀프로젝트 안내(2026년)** 기준, 2~3인 팀의 가상 창업 예산은 **1인당 100만 원**, 발표는 **10분 이내 + 질의응답 5분**, 보고서는 표지 제외 **5쪽 이내**입니다. 제출물은 **보고서와 소스 ZIP 또는 GitHub 링크**이며 발표자료 별도 제출은 필수가 아닙니다. **보고서·코드 마감: 2026-11-23 23:59**, 발표: 11월 24일·26일입니다.

예산은 **출시 준비비 + 출시 후 3개월 운영비**를 합산하고 인건비를 제외하며, 항목별 **공식 가격 출처·조사일·환율 기준**이 필요합니다. 이 저장소는 앱 코드/기술 근거를 제공하지만 **가격 검증된 최종 예산표나 5쪽 보고서를 대체하지 않습니다**. [과제 요건 점검](docs/REQUIREMENTS_CURRENT.md)

## 문서

- [문서 목차](docs/README.md) / [사용 안내](docs/USER_GUIDE.md) / [파일 구조·데이터 흐름](docs/ARCHITECTURE_CURRENT.md)
- [알림 10종](docs/ALARM_FEATURES.md) / [API·Firebase 설정](docs/API_SETUP.md)
- [구현 및 검증 근거](docs/STATUS.md) / [남은 검증](docs/NEXT_STEPS.md) / [시연 점검](docs/UI_LAYOUT_QA.md)
- **[최종 문서·코드 점검 및 잔여 위험](docs/FINAL_REVIEW_2026-11-23.md)**
- [개발 변천사](docs/history/DEVELOPMENT_HISTORY.md) / [NumberPicker 병합 기록](docs/MINUTE_PICKER_EXPERIMENT.md)

## 브랜치와 변경 이력

- **`main`**: 현재 통합 기준. 알림/GPS/추천 작업 [PR #1](https://github.com/jisung3213-bot/my-commute-android/pull/1), 분 입력 선택 UI [PR #2](https://github.com/jisung3213-bot/my-commute-android/pull/2)가 병합되었습니다.
- **`feat/isolated-alert-lab`**: 과거 독립 알림 실험 브랜치. 현재 `main`이 더 최신입니다.
- **`feat/number-picker-inputs`**: 분 입력 UI 실험 브랜치. 이미 `main`에 병합되었으며 복귀 비교를 위해 남겨둘 수 있습니다.

개발 과정은 `docs/history/`에 보관하며, 현재 사용·실행에 불필요한 과거 파일 전체 목록과 커밋별 중복 설명은 README에서 제외했습니다.
