# Firebase와 외부 서비스 설정

## Firebase

1. Firebase Console에 앱 ID `com.example.mycommute`의 Android 앱을 등록합니다.
2. 콘솔에서 내려받은 `google-services.json`을 `app/`에 둡니다.
3. Firebase Authentication에서 이메일/비밀번호 공급자를 활성화합니다. Firestore를 사용할 때는 프로젝트에 [firestore.rules](../firestore.rules)의 규칙을 적용합니다.
4. Android Studio에서 Sync하고 실행한 뒤 가입 또는 로그인합니다.

계정별 루틴과 기본·알림 설정은 기기에도 저장됩니다. Firebase 연결 파일, 사용자 비밀번호, 서비스 키는 Git 관리 대상에서 제외합니다.

## 외부 서비스

`local.properties`에서 기존 `sdk.dir`을 유지합니다. 키 이름은 [local.properties.example](../local.properties.example)을 참고합니다.

| 사용처 | 키 | 확인할 화면 |
| --- | --- | --- |
| TMAP 장소 검색·보행 | `TMAP_APP_KEY` | 장소 검색, 이동의 승차지 보행시간 |
| TMAP 대중교통 | `TMAP_TRANSIT_APP_KEY` | 루틴의 경로 검색과 후보 |
| 기상청 단기예보 | `KMA_SERVICE_KEY` | 루틴의 기상 반영, 실시간 이동의 날씨 |
| 서울 지하철 | `SEOUL_SUBWAY_API_KEY` | 지하철 승차 구간의 실시간 이동 |
| 서울 버스 | `SEOUL_BUS_SERVICE_KEY` | 서울 구간의 버스 도착 정보 |
| 국토교통부 TAGO 버스 | `TAGO_BUS_SERVICE_KEY` | 서울 외 조회 대상 구간의 버스 도착 정보 |

공공데이터포털 공통 키 `PUBLIC_DATA_SERVICE_KEY`를 사용할 수 있습니다. 개별 키가 우선하며 서비스별 활용 승인이 필요할 수 있습니다. 개발용 APK에는 Gradle이 주입한 요청 키가 포함될 수 있으므로 배포 전에 키 관리 방식을 따로 검토합니다.

TMAP Raster SDK 바이너리는 공급사에서 받아 `app/libs/`에 설치합니다. TMAP SDK를 사용할 수 없으면 osmdroid/OpenStreetMap으로 지도 표시를 시도합니다.

## 응답 확인

장소 검색 결과나 추천 후보가 표시되면 요청과 파싱을 함께 확인할 수 있습니다. 차량 시간은 정류장·노선·방향이 확인된 값만 표시합니다. 지도 서버 접근, 교통 기관의 ETA 제공 여부와 요청 권한은 서로 별개입니다. 최근 재현 결과는 [현재 검증 결과](STATUS.md), 과거 실측은 [이전 API 실측](history/API_INTEGRATION_VERIFIED.md)을 봅니다.
