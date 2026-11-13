# 개발 기준 상태

기준일: 2026-11-14 (한국 시간)

## 출처와 범위

Downloads의 `SeoulMobilityApiTester_TMAP_Java_AGP82/SeoulMobilityApiTester`에 있는 실제 Java/XML 프로젝트를 가져왔습니다.
기존 빌드 출력과 IDE 개인 설정은 기준 소스에서 제외했습니다.
지도·로그인 등 새 서비스 기능은 이번 구성 범위에 포함하지 않았습니다.

## 구성 변경

- 현재 작업 폴더를 Android Studio 프로젝트 루트로 구성.
- Gradle 8.2 공식 Wrapper 작업으로 `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` 복구.
- Java/XML, AGP 8.2.0, SDK 및 기존 API 테스트 기능 유지.
- Windows 한글 작업 경로용 Gradle 경로 검사 설정 추가.
- `.gitignore`, `.gitattributes`, 열기·빌드·GitHub 작업 안내 추가.
- 키·SDK 로컬 경로·빌드 출력·서명 파일을 Git 추적에서 제외.

## 검증

| 항목 | 이번 작업 결과 |
| --- | --- |
| Gradle 8.2 Wrapper 생성 | 성공 |
| `gradlew.bat assembleDebug --offline` | 성공, Debug APK 생성 |
| `gradlew.bat lintDebug` | 실패: 기존 `ExpiredTargetSdkVersion` 오류 1건, 경고 61건 |
| Lint 실패 원인 | 원본의 targetSdk 28 유지에 따른 배포 정책 검사. 오류를 숨기거나 전체 검사를 비활성화하지 않음 |
| GitHub | `jisung3213-bot/my-commute-android` 비공개 저장소 생성 및 Git 인증·원격 접근 확인 |
| 실제 기기 실행 | 미검증. 연결된 ADB 기기 없음 |
| Android Studio GUI Sync / Run | 이번 작업에서는 직접 실행하지 않음. 같은 프로젝트의 Gradle 명령 빌드는 성공 |
| 실제 API 호출 | 이번 작업에서는 미실행. 기존 성공 보고는 HANDOFF 문서 참조 |

APK 위치: `app/build/outputs/apk/debug/app-debug.apk` (Git 제외)

Lint 상세 보고서: `app/build/reports/lint-results-debug.html` (로컬 생성)

첫 오프라인 정적 검사에서는 Lint 의존성 캐시가 없어 실패했습니다.
온라인으로 공식 의존성을 받은 뒤 위의 실제 정적 검사 결과를 확인했습니다.
61개 경고에는 기존 테스트 UI의 문자열·접근성·입력 설정 등 개선 사항이 포함됩니다.
기준 프로젝트 구성 후 기능 개발 과정에서 대상 SDK 및 UI 개선을 별도로 진행해야 합니다.
