# 전달 시점 검증 결과

- **입력 자료:** `전달.zip`의 ZIP 엔트리 수는 0개. 기존 프로젝트 소스 없음. `기초 계획안 - Google Docs.pdf`는 이미지형 화면 설계 참고.
- **XML:** AndroidManifest, layout, drawable, values의 XML 파일 총 13개를 XML 파서로 열어 구문 오류가 없음을 확인.
- **리소스:** Java의 `R.id.*` 참조 65개가 XML에 모두 정의되어 있음을 검사. 앱 자체 `R.layout.*` 참조 누락 없음.
- **Java 순수 계산:** `DepartureDecisionEngine.java`를 OpenJDK 21 `javac`로 컴파일하고, 첫 차량 도착시각에서 보행·탑승 여유시간을 뺀 출발 한계시각/즉시 출발/결측값 거부 3개 테스트 통과.
- **앱 전체 빌드:** **미수행**. 현재 환경에 Android SDK, Gradle 및 해당 Android/Firebase 라이브러리가 없어 `assembleDebug`, UI 기기 실행, 시스템 권한·Firebase·TMAP 실제 API 호출을 검증하지 못함.
- **프로젝트 실행 선행조건:** Gradle 8.9 wrapper 생성, Android SDK Platform 35 및 Java 17 환경 확인, Android Studio 동기화. 외부 서비스 실제 시험은 각 공급자 인증정보가 필요함.
- **실시간/자동최적화:** 실제 버스·지하철/기상/멀티모달 API 미연결이므로 기능 검증 대상 아님. 수동 우천 테스트와 계획 기반 알림은 실제 공급자 응답으로 오인하지 않도록 구분.

검증은 파일/코드의 정적 일관성 수준이며, 실제 안드로이드 기기에서 정상 동작을 보증하지 않음.
